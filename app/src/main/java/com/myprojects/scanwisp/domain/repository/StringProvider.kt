package com.myprojects.scanwisp.domain.repository

import androidx.annotation.StringRes

/**
 * Интерфейс для абстрагирования получения строковых ресурсов,
 * позволяющий ViewModel быть независимой от Android Context.
 */
interface StringProvider {
    fun getString(@StringRes resId: Int): String
    fun getString(@StringRes resId: Int, vararg formatArgs: Any): String
}