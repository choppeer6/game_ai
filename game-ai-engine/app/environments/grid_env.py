"""
多智能体网格对抗环境（红蓝双方，独立 PPO 可训练）。
观测：以智能体为中心的 5x5 局部栅格编码（单通道：空地/障碍/己方/敌方/自身）。
"""
from __future__ import annotations

import random
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional, Tuple

import numpy as np


@dataclass
class GridWarParams:
    width: int
    height: int
    red: int
    blue: int
    obstacles: List[Tuple[int, int]] = field(default_factory=list)
    max_steps: int = 200


CELL_EMPTY = 0.0
CELL_OBSTACLE = 0.25
CELL_ALLY = 0.5
CELL_ENEMY = 0.75
CELL_SELF = 1.0


class GridWarParallelEnv:
    """并行多智能体：同一步内所有智能体同时决策。"""

    def __init__(self, params: GridWarParams, seed: Optional[int] = None):
        self.p = params
        self._rng = random.Random(seed)
        self._np_rng = np.random.default_rng(seed)
        self.agent_ids: List[str] = []
        self._pos: Dict[str, Tuple[int, int]] = {}
        self._team: Dict[str, str] = {}
        self._alive: Dict[str, bool] = {}
        self._step_count = 0
        obs_dim = 5 * 5  # 局部栅格展平
        self.obs_dim = obs_dim
        self.n_actions = 5  # noop, up, down, left, right

    def reset(self, seed: Optional[int] = None) -> Dict[str, np.ndarray]:
        if seed is not None:
            self._rng = random.Random(seed)
            self._np_rng = np.random.default_rng(seed)
        self.agent_ids = []
        self._pos.clear()
        self._team.clear()
        self._alive.clear()
        self._step_count = 0
        obs_set = set((r, c) for r, c in self.p.obstacles)
        free: List[Tuple[int, int]] = [
            (r, c)
            for r in range(self.p.height)
            for c in range(self.p.width)
            if (r, c) not in obs_set
        ]
        self._rng.shuffle(free)
        need = self.p.red + self.p.blue
        if len(free) < need:
            raise ValueError(f"可行走格子不足：需要至少 {need} 格，当前仅 {len(free)} 格（请扩大地图或减少障碍/兵力）")
        idx = 0
        for i in range(self.p.red):
            aid = f"red_{i}"
            self.agent_ids.append(aid)
            self._team[aid] = "red"
            self._alive[aid] = True
            self._pos[aid] = free[idx]
            idx += 1
        for i in range(self.p.blue):
            aid = f"blue_{i}"
            self.agent_ids.append(aid)
            self._team[aid] = "blue"
            self._alive[aid] = True
            self._pos[aid] = free[idx]
            idx += 1
        return {aid: self._get_obs(aid) for aid in self.agent_ids}

    def _in_bounds(self, r: int, c: int) -> bool:
        return 0 <= r < self.p.height and 0 <= c < self.p.width

    def _get_obs(self, agent_id: str) -> np.ndarray:
        r0, c0 = self._pos[agent_id]
        patch = np.full((5, 5), CELL_EMPTY, dtype=np.float32)
        team = self._team[agent_id]
        for dr in range(-2, 3):
            for dc in range(-2, 3):
                r, c = r0 + dr, c0 + dc
                pr, pc = dr + 2, dc + 2
                if not self._in_bounds(r, c):
                    patch[pr, pc] = CELL_OBSTACLE
                    continue
                if (r, c) in [(x, y) for x, y in self.p.obstacles]:
                    patch[pr, pc] = CELL_OBSTACLE
                    continue
                occ = self._occupant_at(r, c)
                if occ is None:
                    patch[pr, pc] = CELL_EMPTY
                elif occ == agent_id:
                    patch[pr, pc] = CELL_SELF
                elif self._team[occ] == team:
                    patch[pr, pc] = CELL_ALLY
                else:
                    patch[pr, pc] = CELL_ENEMY
        return patch.reshape(-1)

    def _occupant_at(self, r: int, c: int) -> Optional[str]:
        for aid in self.agent_ids:
            if not self._alive.get(aid, False):
                continue
            if self._pos[aid] == (r, c):
                return aid
        return None

    def step(self, actions: Dict[str, int]) -> Tuple[Dict[str, np.ndarray], Dict[str, float], Dict[str, bool], Dict[str, Any]]:
        """actions: agent_id -> 0..4"""
        rewards = {aid: 0.0 for aid in self.agent_ids}
        self._step_count += 1

        drdc = [(0, 0), (-1, 0), (1, 0), (0, -1), (0, 1)]
        new_pos: Dict[str, Tuple[int, int]] = {}
        for aid in self.agent_ids:
            if not self._alive[aid]:
                continue
            a = int(actions.get(aid, 0)) % self.n_actions
            dr, dc = drdc[a]
            r, c = self._pos[aid]
            nr, nc = r + dr, c + dc
            if not self._in_bounds(nr, nc) or (nr, nc) in self.p.obstacles:
                nr, nc = r, c
            new_pos[aid] = (nr, nc)

        # 冲突：多智能体抢同一格时随机保留一个，其余回退
        pos_to_ids: Dict[Tuple[int, int], List[str]] = {}
        for aid, pos in new_pos.items():
            if not self._alive[aid]:
                continue
            pos_to_ids.setdefault(pos, []).append(aid)
        final_pos: Dict[str, Tuple[int, int]] = {}
        for pos, ids in pos_to_ids.items():
            if len(ids) == 1:
                final_pos[ids[0]] = pos
            else:
                winner = self._rng.choice(ids)
                for x in ids:
                    if x == winner:
                        final_pos[x] = pos
                    else:
                        final_pos[x] = self._pos[x]

        for aid in self.agent_ids:
            if not self._alive[aid]:
                continue
            self._pos[aid] = final_pos[aid]

        # 同格交战：不同队伍同格则双方随机阵亡一方
        cell_map: Dict[Tuple[int, int], List[str]] = {}
        for aid in self.agent_ids:
            if not self._alive[aid]:
                continue
            cell_map.setdefault(self._pos[aid], []).append(aid)
        for pos, ids in cell_map.items():
            teams = {self._team[i] for i in ids}
            if len(teams) < 2:
                continue
            reds = [i for i in ids if self._team[i] == "red"]
            blues = [i for i in ids if self._team[i] == "blue"]
            for _ in range(min(len(reds), len(blues))):
                if not reds or not blues:
                    break
                r_id = self._rng.choice(reds)
                b_id = self._rng.choice(blues)
                if self._rng.random() < 0.5:
                    self._alive[r_id] = False
                    rewards[r_id] -= 1.0
                    for x in self.agent_ids:
                        if self._team[x] == "blue" and self._alive[x]:
                            rewards[x] += 0.3
                else:
                    self._alive[b_id] = False
                    rewards[b_id] -= 1.0
                    for x in self.agent_ids:
                        if self._team[x] == "red" and self._alive[x]:
                            rewards[x] += 0.3
                reds = [i for i in reds if self._alive[i]]
                blues = [i for i in blues if self._alive[i]]

        red_alive = sum(1 for a in self.agent_ids if self._team[a] == "red" and self._alive[a])
        blue_alive = sum(1 for a in self.agent_ids if self._team[a] == "blue" and self._alive[a])
        step_penalty = -0.01
        for aid in self.agent_ids:
            if self._alive[aid]:
                rewards[aid] += step_penalty

        done = False
        info: Dict[str, Any] = {}
        if red_alive == 0 or blue_alive == 0:
            done = True
            winner = "red" if blue_alive == 0 and red_alive > 0 else ("blue" if red_alive == 0 and blue_alive > 0 else "draw")
            info["winner"] = winner
            for aid in self.agent_ids:
                if not self._alive[aid]:
                    continue
                if winner == "draw":
                    rewards[aid] += 0.0
                elif (winner == "red" and self._team[aid] == "red") or (winner == "blue" and self._team[aid] == "blue"):
                    rewards[aid] += 2.0
        elif self._step_count >= self.p.max_steps:
            done = True
            info["winner"] = "draw"
            info["timeout"] = True

        obs_out: Dict[str, np.ndarray] = {}
        for aid in self.agent_ids:
            if self._alive.get(aid, False):
                obs_out[aid] = self._get_obs(aid)
            else:
                obs_out[aid] = np.zeros(self.obs_dim, dtype=np.float32)
        dones = {aid: bool(done or not self._alive.get(aid, False)) for aid in self.agent_ids}
        return obs_out, rewards, dones, info


def parse_scene_to_params(scene_config: Dict[str, Any]) -> GridWarParams:
    import json as _json

    w = int(scene_config.get("map_width", 12))
    h = int(scene_config.get("map_height", 12))
    red = int(scene_config.get("red_count", scene_config.get("red_drone_count", 2)))
    blue = int(scene_config.get("blue_count", scene_config.get("blue_drone_count", 2)))
    extra = scene_config.get("extra") or {}
    if isinstance(extra, str):
        try:
            extra = _json.loads(extra)
        except Exception:
            extra = {}
    if isinstance(extra, dict):
        obs = extra.get("obstacles") or []
        win = extra.get("win_condition") or {}
        max_steps = int(win.get("max_steps", 200))
    else:
        obs = []
        max_steps = 200
    obstacles: List[Tuple[int, int]] = []
    for item in obs:
        if isinstance(item, (list, tuple)) and len(item) >= 2:
            obstacles.append((int(item[0]), int(item[1])))
    return GridWarParams(width=w, height=h, red=red, blue=blue, obstacles=obstacles, max_steps=max_steps)
