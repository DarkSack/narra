package app.narra.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable data object OnboardingRoute

@Serializable data object HomeRoute

@Serializable data object LibraryRoute

@Serializable data object SettingsRoute

@Serializable data class BookRoute(val bookId: String)

@Serializable data object PlayerRoute

/** Análisis y revisión de un libro recién importado ("Tu libro está listo"). */
@Serializable data class ImportRoute(val bookId: String)

/** Estado detallado del procesamiento de un libro. */
@Serializable data class ProcessingRoute(val bookId: String)

/** Selección de voz, para un libro concreto o como valor por defecto. */
@Serializable data class VoicesRoute(val bookId: String? = null)

/** Texto limpio de un capítulo, para revisarlo. */
@Serializable data class ChapterTextRoute(val chapterId: Long, val title: String)

@Serializable data object StorageRoute

@Serializable data object LicensesRoute
