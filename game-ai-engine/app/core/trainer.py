from __future__ import annotations

import json
import os
import threading
import time
from datetime import datetime, timezone
from typing import Any, Dict

import redis

from app.rl_algorithms.ippo_trainer import IPPOTrainer


def _redis_client() -> redis.Redis:
    host = os.environ.get("REDIS_HOST", "127.0.0.1")
    port = int(os.environ.get("REDIS_PORT", "6379"))
    pwd = os.environ.get("REDIS_PASSWORD") or None
    return redis.Redis(host=host, port=port, password=pwd, decode_responses=True)


class JobContext:
    def __init__(self) -> None:
        self.pause = threading.Event()
        self.stop = threading.Event()


class TrainerManager:
    def __init__(self) -> None:
        self._jobs: Dict[str, JobContext] = {}
        self._lock = threading.Lock()

    def _ctx(self, task_id: str) -> JobContext:
        with self._lock:
            if task_id not in self._jobs:
                self._jobs[task_id] = JobContext()
            return self._jobs[task_id]

    def pause(self, task_id: str) -> None:
        self._ctx(task_id).pause.set()

    def resume(self, task_id: str) -> None:
        self._ctx(task_id).pause.clear()

    def stop(self, task_id: str) -> None:
        self._ctx(task_id).stop.set()

    def start_job(self, task_id: str, scene_config: Dict[str, Any], algo_config: Dict[str, Any]) -> None:
        ctx = self._ctx(task_id)
        ctx.pause.clear()
        ctx.stop.clear()
        r = _redis_client()
        max_epochs = int(algo_config.get("max_epochs", algo_config.get("epochs", 20)))
        try:
            trainer = IPPOTrainer(
                task_id=task_id,
                scene_config=scene_config,
                algo_config=algo_config,
                ctx=ctx,
                redis_client=r,
                checkpoint_dir=os.environ.get("CHECKPOINT_DIR", "checkpoints"),
            )
            trainer.run(max_epochs=max_epochs)
        except Exception as e:
            payload = {
                "task_id": task_id,
                "epoch": -1,
                "red_win_rate": 0.0,
                "loss_value": 0.0,
                "cumulative_reward": 0.0,
                "value_estimate": 0.0,
                "done": True,
                "error": str(e),
                "timestamp": datetime.now(timezone.utc).isoformat(),
            }
            r.publish(f"channel:train:metrics:{task_id}", json.dumps(payload))

        if ctx.stop.is_set():
            return

        if max_epochs == 0:
            r.publish(
                f"channel:train:metrics:{task_id}",
                json.dumps(
                    {
                        "task_id": task_id,
                        "epoch": 0,
                        "red_win_rate": 0.0,
                        "loss_value": 0.0,
                        "cumulative_reward": 0.0,
                        "value_estimate": 0.0,
                        "done": True,
                        "timestamp": datetime.now(timezone.utc).isoformat(),
                    }
                ),
            )
