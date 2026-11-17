package io.bsoft.bconverter.core.coroutine

import javax.inject.Qualifier

/** An app-lifetime [kotlinx.coroutines.CoroutineScope] (SupervisorJob): work that must outlive any screen, such as startup housekeeping. */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope
