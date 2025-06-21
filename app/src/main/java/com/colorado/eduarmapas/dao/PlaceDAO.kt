package com.colorado.eduarmapas.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.colorado.eduarmapas.model.Place

//Clase de manipulacion de datos
@Dao
interface PlaceDAO {
    @Insert suspend fun insert(place: Place)
    @Query("SELECT * FROM Place") fun getAll(): LiveData<List<Place>>
}