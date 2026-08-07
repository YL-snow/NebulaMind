"""
云盘智能体 - AI 服务配置
"""
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    ai_service_host: str = '0.0.0.0'
    ai_service_port: int = 8081
    ai_service_api_key: str = ''  # 从环境变量 AI_SERVICE_API_KEY 读取
    debug: bool = True
    llm_provider: str = 'openai'

    wanwu_base_url: str = 'https://api.wanwu.chinaunicom.cn/v1'
    wanwu_api_key: str = ''
    wanwu_api_secret: str = ''
    wanwu_llm_model: str = 'wanwu-llm-pro'
    wanwu_embedding_model: str = 'wanwu-embedding-v1'
    wanwu_reranker_model: str = 'wanwu-reranker-v1'

    openai_base_url: str = 'https://api.openai.com/v1'
    openai_api_key: str = ''
    openai_llm_model: str = 'gpt-4o'
    openai_embedding_model: str = 'text-embedding-3-small'

    milvus_host: str = 'localhost'
    milvus_port: int = 19530
    milvus_collection: str = 'nebulamind_docs'

    rabbitmq_host: str = 'localhost'
    rabbitmq_port: int = 5672
    rabbitmq_username: str = 'guest'
    rabbitmq_password: str = 'guest'
    rabbitmq_exchange: str = 'nebulamind.exchange'
    rabbitmq_upload_queue: str = 'nebulamind.file.upload'
    rabbitmq_delete_queue: str = 'nebulamind.file.delete'
    rabbitmq_processed_queue: str = 'nebulamind.file.processed'

    redis_host: str = 'localhost'
    redis_port: int = 6379
    redis_password: str = ''
    redis_cache_ttl: int = 3600

    backend_base_url: str = 'http://localhost:8080'
    backend_callback_path: str = '/api/v1/files/process-callback'
    backend_api_key: str = ''  # 从环境变量 BACKEND_API_KEY 读取

    encryption_enabled: bool = False
    master_key_base64: str = ''

    model_config = {'env_file': '.env', 'env_file_encoding': 'utf-8', 'extra': 'ignore'}

settings = Settings()
