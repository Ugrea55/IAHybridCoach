package com.garuna.iahybridcoach.data.profile

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude

/* CLAUDE CODE:
 * Perfil del usuario. Se guarda como documento en /users/{uid}.
 *
 * No usa @DocumentId porque el ID del documento es el propio uid del usuario,
 * y eso ya lo conocemos por FirebaseAuth: no necesitamos guardarlo dentro
 * del documento.
 *
 * Valores por defecto en todos los campos para que Firestore pueda
 * deserializar con su constructor sin argumentos.
 *
 * `objetivo` es texto libre: el usuario escribe lo que quiera
 * ("correr 10k en 50 min", "perder 5kg", etc.). Asi le damos contexto rico
 * a la IA sin encajonarle en categorias predefinidas.
 */
data class UserProfile(
    val nombre: String = "",
    val fechaNacimiento: Timestamp = Timestamp.now(),
    val genero: String = Genero.OTRO.name,
    // CLAUDE CODE: alturaCm y pesoKg son datos estables del usuario. NO se
    // piden en onboarding (decision para no alargarlo); se rellenan luego
    // desde la pantalla Perfil. 0 = no introducido todavia.
    val alturaCm: Int = 0,
    val pesoKg: Double = 0.0,
    val objetivo: String = ""
) {
    /** Acceso tipado al genero. Tolerante a valores invalidos. */
    @get:Exclude
    val generoEnum: Genero
        get() = runCatching { Genero.valueOf(genero) }.getOrDefault(Genero.OTRO)

    /** Indica si el formulario de salud debe incluir campos de ciclo menstrual. */
    @get:Exclude
    val incluyeCicloMenstrual: Boolean
        get() = generoEnum != Genero.HOMBRE
}
