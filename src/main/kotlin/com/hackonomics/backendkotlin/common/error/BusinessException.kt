package com.hackonomics.backendkotlin.common.error

class BusinessException(val errorCode: ErrorCode) : RuntimeException(errorCode.message)
