package ch.ralena.natibo.ui.language.importer.worker

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import ch.ralena.natibo.data.room.LanguageRepository
import ch.ralena.natibo.data.room.PackRepository
import ch.ralena.natibo.data.room.`object`.PackRoom
import ch.ralena.natibo.ui.base.BaseViewModel
import ch.ralena.natibo.ui.language.importer.worker.usecase.CountMp3sUseCase
import ch.ralena.natibo.ui.language.importer.worker.usecase.CreateSentencesUseCase
import ch.ralena.natibo.ui.language.importer.worker.usecase.FetchSentencesUseCase
import ch.ralena.natibo.ui.language.importer.worker.usecase.ReadPackDataUseCase
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.lang.Exception
import javax.inject.Inject

class ImportException(message: String) : Exception(message)

class PackImporterViewModel @Inject constructor(
	private val contentResolver: ContentResolver,
	// Repositories
	private val languageRepository: LanguageRepository,
	private val packRepository: PackRepository,
	// Use cases
	private val countMp3sUseCase: CountMp3sUseCase,
	private val createSentencesUseCase: CreateSentencesUseCase,
	private val fetchSentencesUseCase: FetchSentencesUseCase,
	private val readPackDataUseCase: ReadPackDataUseCase
) : BaseViewModel<PackImporterViewModel.Listener>() {
	interface Listener {
		fun onNotificationUpdate(message: String)
		fun onError(exception: ImportException)
	}

	/**
	 * Pulls the language code and pack name out from the Uri filename.
	 */
	suspend fun importPack(uriString: String) {
		val uri = Uri.parse(uriString)
		try {
			// Grab language and pack name
			val (languageCode, packName) = readPackDataUseCase.extractLanguageAndPackName(uri)
			// TODO: Make sure language isn't duplicated when creating it
			val language = createLanguage(languageCode)
			val pack = createPack(packName, languageCode)

			val numMp3s = countMp3sUseCase.countMp3Files(getInputStream(uri))
			val sentences = fetchSentencesUseCase.fetchSentences(getInputStream(uri))
			// TODO: check if numMp3s == sentences.size
			updateNotification(sentences.last())
			createSentencesUseCase.createSentences(language, pack, sentences)
			createSentencesUseCase.sentenceCount()
				.onEach { updateNotification("Reading sentence: $it") }.collect()
			// copy mp3 files over
			countMp3sUseCase.copyMp3s(getInputStream(uri))
			updateNotification("Mp3s copied over")
		} catch (e: ImportException) {
			listeners.forEach { it.onError(e) }
		}
	}

	suspend fun createLanguage(languageCode: String): Long {
		val languageId = languageRepository.createLanguage(languageCode)
		val languages = languageRepository.fetchLanguages()
		Log.d(PackImporterWorker.TAG, languages.toString())
		if (languageId == null)
			throw ImportException("Unable to create language with id: $languageCode")
		return languageId
	}

	private suspend fun createPack(packName: String, languageCode: String): Long {
		var pack = packRepository.fetchPackByNameAndLanguage(packName, languageCode)
		// Create the pack if it doesn't exist already
		if (pack == null) {
			pack = PackRoom(packName, languageCode)
			return packRepository.createPack(pack)
		}
		return pack.id
	}

	// region Helper functions----------------------------------------------------------------------
	private fun updateNotification(message: String) {
		listeners.forEach { it.onNotificationUpdate(message) }
	}

	private fun getInputStream(uri: Uri): InputStream {
		return when (uri.scheme) {
			"file" -> FileInputStream(File(uri.path!!))
			else -> contentResolver.openInputStream(uri)!!
		}
	}
	// endregion Helper functions-------------------------------------------------------------------
}