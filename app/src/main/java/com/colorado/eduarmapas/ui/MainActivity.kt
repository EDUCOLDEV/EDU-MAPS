package com.colorado.eduarmapas.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.colorado.eduarmapas.R
import com.colorado.eduarmapas.databinding.ActivityMainBinding
import com.colorado.eduarmapas.model.Place
import com.colorado.eduarmapas.viewmodel.PlaceViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.gson.Gson
import com.mapbox.geojson.Feature
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.*
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.turf.TurfMeasurement
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var mapBox: MapboxMap
    private lateinit var placeViewModel: PlaceViewModel
    private lateinit var pointAnnotationManager: PointAnnotationManager
    private lateinit var polygonAnnotationManager: PolygonAnnotationManager
    private lateinit var polylineAnnotationManager: PolylineAnnotationManager
    private val selectedPoints = mutableListOf<Point>()
    private val pointAnnotationMap = mutableMapOf<String, PointAnnotation>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        placeViewModel = ViewModelProvider(this)[PlaceViewModel::class.java]

        map = binding.mapView
        mapBox = map.getMapboxMap()
        mapBox.setCamera(CameraOptions.Builder().zoom(14.0).build())

        mapBox.loadStyleUri(Style.MAPBOX_STREETS) { style ->
            val redBitmap = BitmapFactory.decodeResource(resources, R.drawable.red_marker)
            val greenBitmap = BitmapFactory.decodeResource(resources, R.drawable.green_marker)

            style.addImage("red-marker", redBitmap)
            style.addImage("green-marker", greenBitmap)

            val annotationApi = map.annotations
            pointAnnotationManager = annotationApi.createPointAnnotationManager()
            polygonAnnotationManager = annotationApi.createPolygonAnnotationManager()
            polylineAnnotationManager = annotationApi.createPolylineAnnotationManager()

            map.gestures.addOnMapLongClickListener { point ->
                showDialogAddPlace(point)
                true
            }

            getPoints(placeViewModel)

            pointAnnotationManager.addClickListener { annotation ->
                val data = annotation.getData() ?: return@addClickListener false
                val json = data.asJsonObject

                val name = json.getAsJsonObject("properties")?.get("name")?.asString ?: "Sin nombre"
                val date = json.getAsJsonObject("properties")?.get("date")?.asString ?: "Sin fecha"
                val note = json.getAsJsonObject("properties")?.get("note")?.asString ?: "Sin nota"

                val point = annotation.point
                val isSelected = selectedPoints.contains(point)

                val options = arrayOf(
                    "Ver información",
                    if (isSelected) "Deseleccionar" else "Seleccionar"
                )

                AlertDialog.Builder(this)
                    .setTitle("Opciones del marcador")
                    .setItems(options) { _, which ->
                        when (which) {
                            0 -> {
                                val message = "📍 $name\n📅 $date\n📝 $note"
                                AlertDialog.Builder(this)
                                    .setTitle("Información del lugar")
                                    .setMessage(message)
                                    .setPositiveButton("OK", null)
                                    .show()
                            }
                            1 -> {
                                if (isSelected) {
                                    selectedPoints.remove(point)
                                    annotation.iconImage = "red-marker"
                                } else {
                                    selectedPoints.add(point)
                                    annotation.iconImage = "green-marker"
                                }
                                pointAnnotationManager.update(annotation)
                            }
                        }
                    }
                    .show()

                true
            }
        }

        binding.btnZoomIn?.setOnClickListener {
            val zoom = mapBox.cameraState.zoom
            mapBox.setCamera(CameraOptions.Builder().zoom(zoom + 1).build())
        }

        binding.btnZoomOut?.setOnClickListener {
            val zoom = mapBox.cameraState.zoom
            mapBox.setCamera(CameraOptions.Builder().zoom(zoom - 1).build())
        }

        binding.btnOptions?.setOnClickListener {
            showMapOptionsDialog()
        }
    }

    private fun showDialogAddPlace(point: Point) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Nuevo lugar")

        val view = layoutInflater.inflate(R.layout.dialog_add_place, null)
        builder.setView(view)

        val etName = view.findViewById<EditText>(R.id.et_name_place)
        val etNote = view.findViewById<EditText>(R.id.et_notes_place)

        builder.setPositiveButton("Guardar") { dialog, _ ->
            val name = etName.text.toString()
            val note = etNote.text.toString()
            val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

            val place = Place(0, point.latitude(), point.longitude(), name, date, note)
            placeViewModel.insertPlace(place)
            addPoint(place)
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancelar") { dialog, _ ->
            dialog.dismiss()
        }

        builder.show()
    }

    private fun getPoints(placeViewModel: PlaceViewModel) {
        placeViewModel.places.observe(this) { places ->
            places.forEach { place -> addPoint(place) }
        }
    }

    private fun addPoint(place: Place) {
        val point = Point.fromLngLat(place.lng, place.lat)
        val feature = Feature.fromGeometry(point)
        feature.addStringProperty("name", place.name)
        feature.addStringProperty("date", place.date)
        feature.addStringProperty("note", place.notes)

        val annotationOptions = PointAnnotationOptions()
            .withPoint(point)
            .withIconImage("red-marker")

        val annotation = pointAnnotationManager.create(annotationOptions)
        annotation.setData(Gson().toJsonTree(feature))
        pointAnnotationMap[place.name] = annotation
    }

    private fun showMapOptionsDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_map_options, null)
        dialog.setContentView(view)

        view.findViewById<TextView>(R.id.btnCalcularDistancia)?.setOnClickListener {
            val dist = calculateTotalDistance(selectedPoints)
            Toast.makeText(this, "Distancia total: %.2f km".format(dist), Toast.LENGTH_LONG).show()
            dialog.dismiss()
        }

        view.findViewById<TextView>(R.id.btnCalcularArea)?.setOnClickListener {
            val area = calculatePolygonArea(selectedPoints)
            Toast.makeText(this, "Área total: %.2f km²".format(area), Toast.LENGTH_LONG).show()
            dialog.dismiss()
        }

        view.findViewById<TextView>(R.id.btnLimpiarPuntos)?.setOnClickListener {
            selectedPoints.clear()
            pointAnnotationMap.values.forEach { anotation ->
                anotation.iconImage = "red-marker"
                pointAnnotationManager.update(anotation)
            }
            Toast.makeText(this, "Puntos deseleccionados", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        view.findViewById<TextView>(R.id.btnLimpiarMapa)?.setOnClickListener {
            polygonAnnotationManager.deleteAll()
            polylineAnnotationManager.deleteAll()
            Toast.makeText(this, "Dibujo eliminado", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun calculateTotalDistance(points: List<Point>): Double {
        var total = 0.0
        for (i in 0 until points.size - 1) {
            total += TurfMeasurement.distance(points[i], points[i + 1], "kilometers")
        }

        val polylineOptions = PolylineAnnotationOptions()
            .withPoints(selectedPoints)
            .withLineColor("#ff0000")
            .withLineWidth(4.0)

        polylineAnnotationManager.create(polylineOptions)

        return total
    }

    private fun calculatePolygonArea(points: List<Point>): Double {
        if (points.size < 3) return 0.0

        val polygonPoints = points.toMutableList().apply { add(first()) }
        val polygon = Polygon.fromLngLats(listOf(polygonPoints))

        polygonAnnotationManager.deleteAll()
        polygonAnnotationManager.create(
            PolygonAnnotationOptions()
                .withGeometry(polygon)
                .withFillColor("#0000FF")
                .withFillOpacity(0.45)
        )

        return TurfMeasurement.area(polygon) / 1_000_000.0
    }
}
