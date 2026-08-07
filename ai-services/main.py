import sys, os, logging
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from contextlib import asynccontextmanager
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from config import settings

logging.basicConfig(level=logging.DEBUG if settings.debug else logging.INFO, format='%(asctime)s [%(levelname)s] %(name)s: %(message)s', datefmt='%Y-%m-%d %H:%M:%S')
logger = logging.getLogger(__name__)

@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info('=' * 60)
    logger.info('NebulaMind AI Service starting...')
    logger.info(f'LLM Provider: {settings.llm_provider}')
    logger.info(f'Service port: {settings.ai_service_port}')
    logger.info('=' * 60)
    try:
        from app.workers.file_processor import file_processor
        file_processor.start()
    except Exception as e:
        logger.warning(f'File processor worker not started: {e}')
    yield
    logger.info('NebulaMind AI Service shutting down...')
    try:
        from app.workers.file_processor import file_processor
        file_processor.stop()
    except Exception: pass

app = FastAPI(title='NebulaMind AI Service', description='云盘智能体 Python AI 服务', version='1.0.0', lifespan=lifespan, docs_url='/docs', redoc_url='/redoc')
app.add_middleware(CORSMiddleware, allow_origins=['*'], allow_credentials=True, allow_methods=['*'], allow_headers=['*'])

@app.middleware('http')
async def verify_api_key(request: Request, call_next):
    if request.url.path in ('/docs', '/redoc', '/openapi.json', '/health', '/'): return await call_next(request)
    if settings.debug or not settings.ai_service_api_key: return await call_next(request)
    api_key = request.headers.get('X-API-Key', '')
    if api_key != settings.ai_service_api_key: return JSONResponse(status_code=401, content={'detail': 'Invalid or missing API key'})
    return await call_next(request)

from app.api.classify import router as classify_router
from app.api.search import router as search_router
from app.api.qa import router as qa_router
from app.api.generate import router as generate_router
from app.api.sensitive import router as sensitive_router
app.include_router(classify_router); app.include_router(search_router)
app.include_router(qa_router); app.include_router(generate_router)
app.include_router(sensitive_router)

@app.get('/')
async def root():
    from app.core.llm_client import llm_client
    stats = llm_client.get_statistics()
    return {'service': 'NebulaMind AI Service', 'version': '1.0.0', 'llm_provider': settings.llm_provider, 'port': settings.ai_service_port, 'api_docs': f'http://localhost:{settings.ai_service_port}/docs', 'stats': stats}

@app.get('/health')
async def health_check():
    from app.core.llm_client import llm_client
    return {'status': 'healthy', 'service': 'NebulaMind AI Service', 'version': '1.0.0', 'llm_provider': settings.llm_provider, 'stats': llm_client.get_statistics()}

@app.get('/api/v1/stats')
async def get_statistics():
    from app.core.llm_client import llm_client
    return llm_client.get_statistics()

@app.get('/api/v1/logs/export')
async def export_call_logs():
    from app.core.llm_client import llm_client
    return {'total': len(llm_client.call_logs), 'logs': llm_client.export_call_logs()}

@app.get('/api/v1/prompts')
async def list_prompt_templates():
    from app.prompts.templates import PromptManager
    return {'templates': PromptManager.list_templates()}

if __name__ == '__main__':
    import uvicorn
    uvicorn.run('main:app', host=settings.ai_service_host, port=settings.ai_service_port, reload=settings.debug, log_level='info')
