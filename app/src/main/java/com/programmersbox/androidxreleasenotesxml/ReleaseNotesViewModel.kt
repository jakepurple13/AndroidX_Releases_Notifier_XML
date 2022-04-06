package com.programmersbox.androidxreleasenotesxml

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.jsoup.Jsoup

class ReleaseNotesViewModel : ViewModel() {

    private val flowNotes = MutableStateFlow<List<Notes>>(emptyList())

    val notes get() = flowNotes.asSharedFlow()

    fun refreshItems() {
        viewModelScope.launch(Dispatchers.IO) {
            flowNotes.tryEmit(emptyList())
            flowNotes.tryEmit(
                Jsoup.connect("https://developer.android.com/feeds/androidx-release-notes.xml").get()
                    .select("entry")
                    .map {
                        listOf(
                            DateNotes(it.select("title").text()),
                            ReleaseNotes(
                                date = it.select("title").text(),
                                updated = it.select("updated").text(),
                                link = it.select("link").attr("href"),
                                content = it.select("content").text()
                            )
                        )
                    }
                    .flatten()
            )
        }
    }

}

sealed class Notes(val date: String)
class DateNotes(date: String) : Notes(date)
class ReleaseNotes(date: String, val updated: String, val link: String, val content: String) : Notes(date)