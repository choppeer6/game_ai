"""
独立 PPO（红方策略 / 蓝方策略各一个 ActorCritic），适用于 GridWarParallelEnv。
"""
from __future__ import annotations

import json
import os
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional, Tuple

import numpy as np
import torch
import torch.nn.functional as F
import torch.optim as optim

from app.environments.grid_env import GridWarParallelEnv, GridWarParams, parse_scene_to_params
from app.rl_algorithms.actor_critic import ActorCritic


@dataclass
class RolloutBuffer:
    obs: List[np.ndarray]
    actions: List[int]
    rewards: List[float]
    dones: List[bool]
    log_probs: List[float]
    values: List[float]


def _discount_returns(rewards: List[float], dones: List[bool], gamma: float) -> List[float]:
    R = 0.0
    out = [0.0] * len(rewards)
    for t in reversed(range(len(rewards))):
        if dones[t]:
            R = 0.0
        R = rewards[t] + gamma * R
        out[t] = R
    return out


class IPPOTrainer:
    def __init__(
        self,
        task_id: str,
        scene_config: Dict[str, Any],
        algo_config: Dict[str, Any],
        ctx: Any,
        redis_client: Any,
        checkpoint_dir: Optional[str] = None,
    ):
        self.task_id = task_id
        self.scene_config = scene_config
        self.algo_config = algo_config
        self.ctx = ctx
        self.r = redis_client
        self.checkpoint_dir = checkpoint_dir or os.environ.get("CHECKPOINT_DIR", "checkpoints")

        self.params: GridWarParams = parse_scene_to_params(scene_config)
        self.env = GridWarParallelEnv(self.params, seed=42)

        self.lr = float(algo_config.get("learning_rate", 3e-4))
        self.gamma = float(algo_config.get("discount_factor", algo_config.get("gamma", 0.99)))
        self.clip_coef = float(algo_config.get("clip_coef", 0.2))
        self.vf_coef = float(algo_config.get("vf_coef", 0.5))
        self.ent_coef = float(algo_config.get("ent_coef", 0.01))
        self.rollout_episodes = int(algo_config.get("rollout_episodes", 6))
        self.ppo_epochs = int(algo_config.get("ppo_epochs", 4))
        self.max_grad_norm = float(algo_config.get("max_grad_norm", 0.5))
        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

        od = self.env.obs_dim
        na = self.env.n_actions
        self.policy_red = ActorCritic(od, na).to(self.device)
        self.policy_blue = ActorCritic(od, na).to(self.device)
        self.opt_red = optim.Adam(self.policy_red.parameters(), lr=self.lr)
        self.opt_blue = optim.Adam(self.policy_blue.parameters(), lr=self.lr)

    def _policy_for(self, agent_id: str) -> ActorCritic:
        return self.policy_red if agent_id.startswith("red") else self.policy_blue

    def _collect_episode(self) -> Tuple[List[RolloutBuffer], List[RolloutBuffer], str]:
        """返回红/蓝 buffer 列表（每智能体一条）及胜者标签。"""
        obs_cur = self.env.reset()
        buffers: Dict[str, RolloutBuffer] = {aid: RolloutBuffer([], [], [], [], [], []) for aid in self.env.agent_ids}

        winner = "draw"
        steps = 0
        while True:
            if self.ctx.stop.is_set():
                break
            actions: Dict[str, int] = {}
            acted: List[str] = []
            for aid in self.env.agent_ids:
                if not self.env._alive.get(aid, False):
                    continue
                o = torch.tensor(obs_cur[aid], dtype=torch.float32, device=self.device).unsqueeze(0)
                pol = self._policy_for(aid)
                with torch.no_grad():
                    action, log_prob, _, value = pol.act(o)
                actions[aid] = int(action.item())
                acted.append(aid)
                b = buffers[aid]
                b.obs.append(obs_cur[aid].copy())
                b.actions.append(actions[aid])
                b.log_probs.append(float(log_prob.item()))
                b.values.append(float(value.item()))

            next_obs, rewards, dones, info = self.env.step(actions)
            steps += 1
            for aid in acted:
                buffers[aid].rewards.append(float(rewards.get(aid, 0.0)))
                buffers[aid].dones.append(bool(dones.get(aid, False)))

            obs_cur = next_obs

            if info.get("winner"):
                winner = str(info["winner"])
            if info.get("winner") or steps >= self.params.max_steps:
                break
            if self.ctx.stop.is_set():
                break

        red_bufs = [buffers[a] for a in self.env.agent_ids if a.startswith("red")]
        blue_bufs = [buffers[a] for a in self.env.agent_ids if a.startswith("blue")]
        return red_bufs, blue_bufs, winner

    def _ppo_update(self, policy: ActorCritic, optimizer: optim.Adam, buffers: List[RolloutBuffer]) -> float:
        if not buffers or all(len(b.obs) == 0 for b in buffers):
            return 0.0
        obs_all: List[np.ndarray] = []
        act_all: List[int] = []
        ret_all: List[float] = []
        old_logp_all: List[float] = []
        for buf in buffers:
            if len(buf.obs) == 0:
                continue
            ret = _discount_returns(buf.rewards, buf.dones, self.gamma)
            for i in range(len(buf.obs)):
                obs_all.append(buf.obs[i])
                act_all.append(buf.actions[i])
                ret_all.append(ret[i])
                old_logp_all.append(buf.log_probs[i])

        if not obs_all:
            return 0.0

        obs_t = torch.tensor(np.stack(obs_all), dtype=torch.float32, device=self.device)
        act_t = torch.tensor(act_all, dtype=torch.long, device=self.device)
        ret_t = torch.tensor(ret_all, dtype=torch.float32, device=self.device)
        old_logp_t = torch.tensor(old_logp_all, dtype=torch.float32, device=self.device)

        total_loss = 0.0
        n_updates = 0
        n = obs_t.shape[0]
        idx = torch.randperm(n, device=self.device)

        for _ in range(self.ppo_epochs):
            for start in range(0, n, max(1, n // 4)):
                end = min(start + max(1, n // 4), n)
                batch_idx = idx[start:end]
                ob = obs_t[batch_idx]
                ac = act_t[batch_idx]
                rt = ret_t[batch_idx]
                old_lp = old_logp_t[batch_idx]

                log_prob, entropy, values = policy.evaluate(ob, ac)
                ratio = torch.exp(log_prob - old_lp)
                adv = rt - values.detach()
                adv = (adv - adv.mean()) / (adv.std() + 1e-8)
                surr1 = ratio * adv
                surr2 = torch.clamp(ratio, 1.0 - self.clip_coef, 1.0 + self.clip_coef) * adv
                pi_loss = -torch.min(surr1, surr2).mean()
                v_loss = F.mse_loss(values, rt)
                loss = pi_loss + self.vf_coef * v_loss - self.ent_coef * entropy.mean()

                optimizer.zero_grad()
                loss.backward()
                torch.nn.utils.clip_grad_norm_(policy.parameters(), self.max_grad_norm)
                optimizer.step()
                total_loss += float(loss.item())
                n_updates += 1

        return total_loss / max(1, n_updates)

    def _eval_win_rate(self, n_games: int = 8) -> float:
        wins = 0
        for g in range(n_games):
            obs0 = self.env.reset(seed=1000 + g)
            steps = 0
            while steps < self.params.max_steps:
                actions: Dict[str, int] = {}
                for aid in self.env.agent_ids:
                    if not self.env._alive.get(aid, False):
                        continue
                    o = torch.tensor(obs0[aid], dtype=torch.float32, device=self.device).unsqueeze(0)
                    pol = self._policy_for(aid)
                    with torch.no_grad():
                        logits, _ = pol.forward(o)
                        actions[aid] = int(torch.argmax(logits, dim=-1).item())
                obs0, _, dones, info = self.env.step(actions)
                steps += 1
                if info.get("winner") == "red":
                    wins += 1
                    break
                if info.get("winner") == "blue":
                    break
                if all(dones.get(aid, True) for aid in self.env.agent_ids):
                    break
        return wins / float(n_games)

    def run(self, max_epochs: int) -> None:
        os.makedirs(os.path.join(self.checkpoint_dir, self.task_id), exist_ok=True)
        cum_reward_ema = 0.0
        win_ema = 0.45

        for epoch in range(1, max_epochs + 1):
            if self.ctx.stop.is_set():
                break
            while self.ctx.pause.is_set() and not self.ctx.stop.is_set():
                time.sleep(0.2)

            ep_returns: List[float] = []
            losses: List[float] = []
            winners: List[str] = []
            red_all: List[RolloutBuffer] = []
            blue_all: List[RolloutBuffer] = []

            for _ in range(self.rollout_episodes):
                if self.ctx.stop.is_set():
                    break
                red_b, blue_b, w = self._collect_episode()
                winners.append(w)
                red_all.extend(red_b)
                blue_all.extend(blue_b)
                for buf in red_b + blue_b:
                    if buf.rewards:
                        ep_returns.append(float(np.sum(buf.rewards)))

            lr = self._ppo_update(self.policy_red, self.opt_red, red_all)
            lb = self._ppo_update(self.policy_blue, self.opt_blue, blue_all)
            losses.append((lr + lb) / 2.0)

            wr = winners.count("red") / max(1, len(winners))
            win_ema = 0.95 * win_ema + 0.05 * wr
            if ep_returns:
                cum_reward_ema = 0.95 * cum_reward_ema + 0.05 * float(np.mean(ep_returns))
            loss_val = float(np.mean(losses)) if losses else 0.0

            with torch.no_grad():
                z = torch.zeros(1, self.env.obs_dim, device=self.device)
                _, v1 = self.policy_red.forward(z)
                _, v2 = self.policy_blue.forward(z)
                value_estimate = (float(v1.item()) + float(v2.item())) / 2.0

            eval_wr = self._eval_win_rate(n_games=6)
            channel = f"channel:train:metrics:{self.task_id}"
            ck_dir = os.path.join(self.checkpoint_dir, self.task_id)
            payload = {
                "task_id": self.task_id,
                "epoch": epoch,
                "red_win_rate": float(eval_wr),
                "loss_value": float(loss_val),
                "cumulative_reward": float(cum_reward_ema),
                "value_estimate": float(value_estimate),
                "done": epoch == max_epochs and not self.ctx.stop.is_set(),
                "checkpoint_path": ck_dir,
                "timestamp": datetime.now(timezone.utc).isoformat(),
            }
            self.r.publish(channel, json.dumps(payload))

            if epoch % 10 == 0 or epoch == max_epochs:
                path_r = os.path.join(self.checkpoint_dir, self.task_id, f"policy_red_ep{epoch}.pt")
                path_b = os.path.join(self.checkpoint_dir, self.task_id, f"policy_blue_ep{epoch}.pt")
                torch.save(self.policy_red.state_dict(), path_r)
                torch.save(self.policy_blue.state_dict(), path_b)

            time.sleep(0.02)

        # 最终模型
        if not self.ctx.stop.is_set():
            torch.save(self.policy_red.state_dict(), os.path.join(self.checkpoint_dir, self.task_id, "policy_red_final.pt"))
            torch.save(self.policy_blue.state_dict(), os.path.join(self.checkpoint_dir, self.task_id, "policy_blue_final.pt"))
