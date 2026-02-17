package com.castor.core.common.util

sealed interface CastorResult<out T> {
    data class Success<T>(val data: T) : CastorResult<T>
    data class Error(val message: String, val throwable: Throwable? = null) : CastorResult<Nothing>
    data object Loading : CastorResult<Nothing>
}
