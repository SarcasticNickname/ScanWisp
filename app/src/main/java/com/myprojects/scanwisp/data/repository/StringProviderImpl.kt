package com.myprojects.scanwisp.data.repository

import android.content.Context
import androidx.annotation.StringRes
import com.myprojects.scanwisp.domain.repository.StringProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StringProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : StringProvider {
    override fun getString(@StringRes resId: Int): String = context.getString(resId)

    override fun getString(@StringRes resId: Int, vararg formatArgs: Any): String =
        context.getString(resId, *formatArgs)
}