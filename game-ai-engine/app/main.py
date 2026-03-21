import os
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api import eval_rollout, infer, train

app = FastAPI(title="game-ai-engine", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
app.include_router(train.router, prefix="")
app.include_router(infer.router, prefix="")
app.include_router(eval_rollout.router, prefix="")


@app.get("/health")
def health():
    return {"status": "UP", "service": "game-ai-engine"}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app.main:app", host="0.0.0.0", port=int(os.environ.get("PORT", "8000")), reload=True)
