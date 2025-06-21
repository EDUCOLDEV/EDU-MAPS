package com.colorado.eduarmapas.model

import androidx.room.Entity
import androidx.room.PrimaryKey

//Modelo de la tabla que se va a guardar en la base de datos
@Entity
data class Place (
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val lat: Double,
    val lng: Double,
    val name: String,
    val date: String,
    val notes: String
)