package br.com.radioembarcada.storage

import java.io.File

interface SavedProgrammingConfiguration {
    fun read(): String?
    fun write(json: String)
}

/** Reutiliza a gravação atômica de JSON; arquivo privado separado do catálogo musical. */
class FileProgrammingConfiguration(file: File) : SavedProgrammingConfiguration {
    private val document = FileMusicCatalog(file)
    override fun read(): String? = document.read()
    override fun write(json: String) = document.write(json)
}
