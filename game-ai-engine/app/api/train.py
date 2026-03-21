from __future__ import annotations

import threading
from typing import Any, Dict

from fastapi import APIRouter
from pydantic import BaseModel

from app.core.trainer import TrainerManager

router = APIRouter()
_manager = TrainerManager()


class TrainStartBody(BaseModel):
    task_id: str
    scene_config: Dict[str, Any]
    algo_config: Dict[str, Any]


class TaskIdBody(BaseModel):
    task_id: str


@router.post("/api/train/start")
def start_train(body: TrainStartBody):
    threading.Thread(target=_manager.start_job, args=(body.task_id, body.scene_config, body.algo_config), daemon=True).start()
    return {"ok": True, "task_id": body.task_id}


@router.post("/api/train/pause")
def pause_train(body: TaskIdBody):
    _manager.pause(body.task_id)
    return {"ok": True}


@router.post("/api/train/resume")
def resume_train(body: TaskIdBody):
    _manager.resume(body.task_id)
    return {"ok": True}


@router.post("/api/train/stop")
def stop_train(body: TaskIdBody):
    _manager.stop(body.task_id)
    return {"ok": True}
