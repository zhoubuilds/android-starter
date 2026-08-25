package com.whisper.foundation.model.business

import com.whisper.architecture.business.model.ArchitectureBusiness

/**
 * 表示当前应用的业务执行状态.
 */
typealias Business<T> = ArchitectureBusiness<T, BusinessMetadata>

/**
 * 表示当前应用不包含加载中状态的业务结果.
 */
typealias BusinessOutcome<T> = ArchitectureBusiness.Outcome<T, BusinessMetadata>

/**
 * 表示当前应用的业务成功状态.
 */
typealias BusinessSuccess<T> = ArchitectureBusiness.Success<T, BusinessMetadata>

/**
 * 表示当前应用的业务错误状态.
 */
typealias BusinessError<T> = ArchitectureBusiness.Error<T, BusinessMetadata>
