"""
固定种子多局贪心 rollout 评估（与训练环境一致），用于报告与答辩演示。
"""
from __future__ import annotations

import os
from typing import Any, Dict, List, Optional

import torch
from fastapi import APIRouter
from pydantic import BaseModel

from app.environments.grid_env import GridWarParallelEnv, parse_scene_to_params
from app.rl_algorithms.actor_critic import ActorCritic

router = APIRouter()


class EvalRolloutRequest(BaseModel):
    checkpoint_dir: str
    scene_config: Optional[Dict[str, Any]] = None
    n_episodes: int = 16
    seed: int = 42


@router.post("/api/eval/rollout")
def eval_rollout(body: EvalRolloutRequest) -> Dict[str, Any]:
    scene = body.scene_config or {
        "map_width": 16,
        "map_height": 16,
        "red_count": 2,
        "blue_count": 2,
        "extra": {"obstacles": [], "win_condition": {"max_steps": 200}},
    }
    params = parse_scene_to_params(scene)
    env = GridWarParallelEnv(params, seed=body.seed)

    red_path = os.path.join(body.checkpoint_dir, "policy_red_final.pt")
    blue_path = os.path.join(body.checkpoint_dir, "policy_blue_final.pt")
    if not os.path.isfile(red_path) or not os.path.isfile(blue_path):
        return {"error": f"需要 {red_path} 与 {blue_path} 存在"}

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    od, na = env.obs_dim, env.n_actions
    pr = ActorCritic(od, na).to(device)
    pb = ActorCritic(od, na).to(device)
    pr.load_state_dict(torch.load(red_path, map_location=device))
    pb.load_state_dict(torch.load(blue_path, map_location=device))
    pr.eval()
    pb.eval()

    red_w = blue_w = draws = 0
    returns_red: List[float] = []

    for g in range(body.n_episodes):
        obs = env.reset(seed=body.seed + g)
        ep_ret_red = 0.0
        steps = 0
        while steps < params.max_steps:
            actions: Dict[str, int] = {}
            for aid in env.agent_ids:
                if not env._alive.get(aid, False):
                    continue
                o = torch.tensor(obs[aid], dtype=torch.float32, device=device).unsqueeze(0)
                pol = pr if aid.startswith("red") else pb
                with torch.no_grad():
                    logits, _ = pol.forward(o)
                    actions[aid] = int(torch.argmax(logits, dim=-1).item())
            obs, rewards, _, info = env.step(actions)
            for aid in env.agent_ids:
                if aid.startswith("red") and aid in rewards:
                    ep_ret_red += float(rewards[aid])
            steps += 1
            w = info.get("winner")
            if w:
                if w == "red":
                    red_w += 1
                elif w == "blue":
                    blue_w += 1
                else:
                    draws += 1
                break
        else:
            draws += 1
        returns_red.append(ep_ret_red)

    n = body.n_episodes
    return {
        "n_episodes": n,
        "red_wins": red_w,
        "blue_wins": blue_w,
        "draws": draws,
        "red_win_rate": red_w / n,
        "mean_red_return": float(sum(returns_red) / max(1, len(returns_red))),
        "scene": {"width": params.width, "height": params.height, "max_steps": params.max_steps},
    }
