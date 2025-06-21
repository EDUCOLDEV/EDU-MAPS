package com.colorado.eduarmapas.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.colorado.eduarmapas.dao.PlaceDAO
import com.colorado.eduarmapas.model.Place

//Configuracion de la base de datos en Room
@Database(entities = [Place::class], version = 1)
abstract class DataBase : RoomDatabase() {
    abstract fun PlaceDAO(): PlaceDAO
}