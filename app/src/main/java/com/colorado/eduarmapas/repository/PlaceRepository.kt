package com.colorado.eduarmapas.repository

import com.colorado.eduarmapas.dao.PlaceDAO
import com.colorado.eduarmapas.model.Place

class PlaceRepository(private val dao: PlaceDAO) {
    val places = dao.getAll();
    suspend fun insert(place: Place) = dao.insert(place)
}