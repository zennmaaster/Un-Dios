package com.castor.core.inference.di

import com.castor.core.inference.InferenceEngine
import com.castor.core.inference.embedding.EmbeddingEngine
import com.castor.core.inference.embedding.LlamaEmbeddingEngine
import com.castor.core.inference.llama.LlamaCppEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class InferenceModule {

    @Binds
    @Singleton
    abstract fun bindInferenceEngine(impl: LlamaCppEngine): InferenceEngine

    @Binds
    @Singleton
    abstract fun bindEmbeddingEngine(impl: LlamaEmbeddingEngine): EmbeddingEngine
}
