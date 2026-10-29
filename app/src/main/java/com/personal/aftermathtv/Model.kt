package com.personal.aftermathtv

data class Program(
    val title: String,
    val start: Long,
    val end: Long,
    val episode: String,
    val block: String,
    val year: Int,
    val type: String,
    val imageUrl: String,
    val lookup: String = "",
    val note: String = ""
)

data class GuideRow(
    val id: String,
    val label: String,
    val programs: List<Program>,
    val note: String
)

data class Meta(
    val title: String,
    val plot: String,
    val year: String,
    val rating: String,
    val genres: String,
    val poster: String
)
