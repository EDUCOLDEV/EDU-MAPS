package com.colorado.eduarmapas.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.colorado.eduarmapas.data.DataBase
import com.colorado.eduarmapas.model.Place
import com.colorado.eduarmapas.repository.PlaceRepository
import kotlinx.coroutines.launch

//View model para observar los datos
class PlaceViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Room.databaseBuilder(application, DataBase::class.java, "places_db").build()
    private val repository = PlaceRepository(db.PlaceDAO())

    val places = repository.places;

    //Funcion para incerta los lugares
    fun insertPlace(place: Place) = viewModelScope.launch { repository.insert(place) }
}