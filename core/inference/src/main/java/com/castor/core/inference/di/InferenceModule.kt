package com.castor.core.inference.di

import com.castor.core.inference.InferenceEngine
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
}
