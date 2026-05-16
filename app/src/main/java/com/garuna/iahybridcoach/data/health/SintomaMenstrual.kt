package com.garuna.iahybridcoach.data.health

/* CLAUDE CODE:
 * Sintomas menstruales tipicos. El usuario marca los que aplican.
 * Se persiste en Firestore como List<String> con los nombres del enum.
 *
 * Mantener nombres ESTABLES; el label puede cambiar sin romper datos.
 */
enum class SintomaMenstrual(val label: String) {
    CALAMBRES("Calambres"),
    DOLOR_CABEZA("Dolor de cabeza"),
    DOLOR_ESPALDA("Dolor de espalda"),
    HINCHAZON("Hinchazon"),
    FATIGA("Fatiga"),
    CAMBIOS_ANIMO("Cambios de animo"),
    ANTOJOS("Antojos"),
    ACNE("Acne"),
    SENSIBILIDAD_PECHO("Sensibilidad en el pecho")
}
