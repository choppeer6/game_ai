"""
最小推理接口：加载已训练 ActorCritic 权重，对给定观测向量输出离散动作（用于部署演示）。
"""
from __future__ import annotations

import os
from typing import Any, Dict, List

import torch
from fastapi import APIRouter
from pydantic import BaseModel

from app.rl_algorithms.actor_critic import ActorCritic

router = APIRouter()


class InferRequest(BaseModel):
    checkpoint_dir: str
    team: str = "red"  # red | blue
    observation: List[float]


@router.post("/api/infer/predict")
def predict(body: InferRequest) -> Dict[str, Any]:
    obs_dim = len(body.observation)
    if obs_dim != 25:
        return {"error": f"observation 维度应为 25（5x5 局部栅格），当前 {obs_dim}"}
    fn = "policy_red_final.pt" if body.team == "red" else "policy_blue_final.pt"
    path = os.path.join(body.checkpoint_dir, fn)
    if not os.path.isfile(path):
        return {"error": f"未找到权重文件: {path}"}
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    net = ActorCritic(obs_dim, 5).to(device)
    net.load_state_dict(torch.load(path, map_location=device))
    net.eval()
    x = torch.tensor([body.observation], dtype=torch.float32, device=device)
    with torch.no_grad():
        logits, value = net.forward(x)
        action = int(torch.argmax(logits, dim=-1).item())
    return {"action": action, "value": float(value.item()), "logits": logits.cpu().tolist()[0]}
