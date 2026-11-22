import sys
from pathlib import Path

AI_SERVER_DIR = Path(__file__).resolve().parent.parent / "ai-server"
sys.path.insert(0, str(AI_SERVER_DIR))

from main import app
