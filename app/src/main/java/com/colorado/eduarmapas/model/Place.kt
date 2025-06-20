package com.colorado.eduarmapas.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Place (
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val lat: Double,
    val lng: Double,
    val name: String,
    val date: String,
    val notes: String
)