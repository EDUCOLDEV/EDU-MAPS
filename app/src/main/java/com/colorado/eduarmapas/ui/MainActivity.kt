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

        //Se usa binding para crear la pagina principal
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //Se utiliza un ViewModel para acceder y observar los datos
        placeViewModel = ViewModelProvider(this)[PlaceViewModel::class.java]

        //Se instancias las clases a usar
        map = binding.mapView
        mapBox = map.getMapboxMap()
        mapBox.setCamera(CameraOptions.Builder().zoom(14.0).build())

        //Se configura el mapbox
        mapBox.loadStyleUri(Style.MAPBOX_STREETS) { style ->
            val redBitmap = BitmapFactory.decodeResource(resources, R.drawable.red_marker)
            val greenBitmap = BitmapFactory.decodeResource(resources, R.drawable.green_marker)

            //Se agregan los marcadores
            style.addImage("red-marker", redBitmap)
            style.addImage("green-marker", greenBitmap)

            //Se agragan las anotaciones para crear puntos, poligonos y lineas
            val annotationApi = map.annotations
            pointAnnotationManager = annotationApi.createPointAnnotationManager()
            polygonAnnotationManager = annotationApi.createPolygonAnnotationManager()
            polylineAnnotationManager = annotationApi.createPolylineAnnotationManager()

            //Se agregar un gesto para realizar una accion al mapa (Presion proplongada en la pantalla)
            map.gestures.addOnMapLongClickListener { point ->
                showDialogAddPlace(point)
                true
            }

            //Se obtinen los puntos de la base de datos
            getPoints(placeViewModel)

            //Se agrega un evento al punto para ver información
            pointAnnotationManager.addClickListener { annotation ->
                //Se recupera la data guardad en cada punto
                val data = annotation.getData() ?: return@addClickListener false
                val json = data.asJsonObject

                //Se guardan los datos recuperados en las variables
                val name = json.getAsJsonObject("properties")?.get("name")?.asString ?: "Sin nombre"
                val date = json.getAsJsonObject("properties")?.get("date")?.asString ?: "Sin fecha"
                val note = json.getAsJsonObject("properties")?.get("note")?.asString ?: "Sin nota"

                //Se recuperan la ubicacion del puntp
                val point = annotation.point
                val isSelected = selectedPoints.contains(point)

                //Se creear las opciones para un alert dialog
                val options = arrayOf(
                    "Ver información",
                    if (isSelected) "Deseleccionar" else "Seleccionar"
                )


                //Se construye el alertdialog y se ejecuta una accion de acuerdo a la opcion seleccionada
                AlertDialog.Builder(this, R.style.AlertDialogTheme) //Se pasa el contexto y el tema
                    .setTitle("Opciones del marcador")              //Se agrega titulo
                    .setItems(options) { _, which ->                //Se agrega las opciones
                        when (which) {
                            0 -> {                                  //En caso de primera opcion seleccionada
                                val message = "📍 $name\n📅 $date\n📝 $note"
                                AlertDialog.Builder(this, R.style.AlertDialogTheme)   //Se abre otro dialogo con informacion del punto
                                    .setTitle("Información del lugar")
                                    .setMessage(message)
                                    .setPositiveButton("OK", null)
                                    .show()
                            }
                            1 -> {                                  //En caso de segunds opccion seleccionada
                                if (isSelected) {                   //Se comprueba si el punto esta seleccionadp
                                    selectedPoints.remove(point)    //Se deselecciona el punto
                                    annotation.iconImage = "red-marker" //Se cambia el marcado
                                } else {
                                    selectedPoints.add(point)       //Se selecciona el punto
                                    annotation.iconImage = "green-marker"   //Se cambia el marcador
                                }
                                pointAnnotationManager.update(annotation)   //Se actualiza el punto
                            }
                        }
                    }
                    .show()

                true
            }
        }

        //Evento click para acercar el zoom
        binding.btnZoomIn?.setOnClickListener {
            val zoom = mapBox.cameraState.zoom
            mapBox.setCamera(CameraOptions.Builder().zoom(zoom + 1).build())
        }

        //Evento click para alejar el zoom
        binding.btnZoomOut?.setOnClickListener {
            val zoom = mapBox.cameraState.zoom
            mapBox.setCamera(CameraOptions.Builder().zoom(zoom - 1).build())
        }

        //Evento click para abrir las opciones
        binding.btnOptions?.setOnClickListener {
            showMapOptionsDialog()
        }
    }

    //Metodo para agregar puntos con informacion
    private fun showDialogAddPlace(point: Point) {
        //Se contruye el cuadro de dialogo donde se ingresara la información
        val builder = AlertDialog.Builder(this, R.style.AlertDialogTheme)
        builder.setTitle("Nuevo lugar")

        //Se le agrega un layout perzonalizado
        val view = layoutInflater.inflate(R.layout.dialog_add_place, null)
        builder.setView(view)

        //Se recupera los inputs del layout personalizado
        val etName = view.findViewById<EditText>(R.id.et_name_place)
        val etNote = view.findViewById<EditText>(R.id.et_notes_place)

        //Se configuran los botones
        builder.setPositiveButton("Guardar") { dialog, _ ->
            //Se registra la informacción ingresada
            val name = etName.text.toString()
            val note = etNote.text.toString()
            val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

            //Se crea el modelo
            val place = Place(0, point.latitude(), point.longitude(), name, date, note)
            //Se guarda en la base de datos
            placeViewModel.insertPlace(place)

            //Se agrega el putno
            addPoint(place)

            dialog.dismiss()
        }

        builder.setNegativeButton("Cancelar") { dialog, _ ->
            dialog.dismiss()
        }

        builder.show()
    }

    //Metodo para obtener los puntos
    private fun getPoints(placeViewModel: PlaceViewModel) {
        //Se recupera los puntos de la base de datos y se agrega al mapa
        placeViewModel.places.observe(this) { places ->
            places.forEach { place -> addPoint(place) }
        }
    }

    //Metodo para agregar puntos al mapa
    private fun addPoint(place: Place) {
        //Se crea el punto
        val point = Point.fromLngLat(place.lng, place.lat)

        //Se recupera los datos
        val feature = Feature.fromGeometry(point)
        feature.addStringProperty("name", place.name)
        feature.addStringProperty("date", place.date)
        feature.addStringProperty("note", place.notes)

        //Se crea la anotacion con el diseño del marcador y la ubicacion
        val annotationOptions = PointAnnotationOptions()
            .withPoint(point)
            .withIconImage("red-marker")

        //Se crea el punto con la anotacion
        val annotation = pointAnnotationManager.create(annotationOptions)
        //Se guarda la informacion del punto en la anotaccion en formato JSON
        annotation.setData(Gson().toJsonTree(feature))
        pointAnnotationMap[place.name] = annotation
    }

    //Metodo para mostrar el menu inferior con las opciones
    private fun showMapOptionsDialog() {
        //Se contruye el BottomSheetDialog
        val dialog = BottomSheetDialog(this)

        //Se agrega el layout personalizado
        val view = layoutInflater.inflate(R.layout.dialog_map_options, null)
        dialog.setContentView(view)

        //Se agregan las acciones de acuerdo a las opciones seleccionadas
        //OPCION PARA CALCULAR DISTANCIA
        view.findViewById<TextView>(R.id.btnCalcularDistancia)?.setOnClickListener {
            val dist = calculateTotalDistance(selectedPoints)
            if(dist > 0){
                Toast.makeText(this, "Distancia total: %.2f km".format(dist), Toast.LENGTH_LONG).show()
            }
            dialog.dismiss()
        }

        //OPCION PARA CALCULAR AREA
        view.findViewById<TextView>(R.id.btnCalcularArea)?.setOnClickListener {
            val area = calculatePolygonArea(selectedPoints)
            if(area > 0){
                Toast.makeText(this, "Área total: %.2f km²".format(area), Toast.LENGTH_LONG).show()
            }
            dialog.dismiss()
        }

        //OPCION PARA LIMPIAR PUNTOS SELECCIONADOS
        view.findViewById<TextView>(R.id.btnLimpiarPuntos)?.setOnClickListener {
            selectedPoints.clear()
            pointAnnotationMap.values.forEach { anotation ->
                //Se cambia el icono seleccionado al icono no seleccionado
                anotation.iconImage = "red-marker"

                //Se actualiza el punto
                pointAnnotationManager.update(anotation)

                //Se borran los dibujos si es que hubieran
                polygonAnnotationManager.deleteAll()
                polylineAnnotationManager.deleteAll()
            }
            Toast.makeText(this, "Puntos deseleccionados", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        //OPCION PARA LIMPIAR DIBUJO
        view.findViewById<TextView>(R.id.btnLimpiarMapa)?.setOnClickListener {
            //Se borran los dibujos si hay
            polygonAnnotationManager.deleteAll()
            polylineAnnotationManager.deleteAll()
            Toast.makeText(this, "Dibujo eliminado", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    //Metodo para calcular la distancia entre puntos
    private fun calculateTotalDistance(points: List<Point>): Double {
        //Se inicia una variable total con valor 0
        var total = 0.0

        //Se hace un recorrido por los puntos seleccionado para ir calculando las dinstancias en KM
        for (i in 0 until points.size - 1) {
            total += TurfMeasurement.distance(points[i], points[i + 1], "kilometers")
        }

        //Se selecciona el color de la linea
        val colorHex = String.format("#%06X", 0xFFFFFF and getColor(R.color.colorMarkerSelected))

        //Se configura las lineas que se van a pintar
        val polylineOptions = PolylineAnnotationOptions()
            .withPoints(selectedPoints)
            .withLineColor(colorHex)
            .withLineWidth(4.0)

        //Se pinta lineas en el mapta trazando las distancias
        polylineAnnotationManager.create(polylineOptions)

        return total
    }

    //eMetodo para calcular el area
    private fun calculatePolygonArea(points: List<Point>): Double {

        //Se comprueba si hay minimo 3 puntos para calcular el area
        if (points.size < 3) {
            Toast.makeText(this, "Debe seleccionar minimo 3 puntos para calcular el area", Toast.LENGTH_LONG).show()
            return 0.0
        }

        //Se crea una lista de puntos, agregando el primer punto al final para cerrar el poligono
        val polygonPoints = points.toMutableList().apply { add(first()) }

        //Se crea el poligono de acuerdo a los puntos seleccionados
        val polygon = Polygon.fromLngLats(listOf(polygonPoints))

        //Se elige el color para pintar el area dibujada
        val colorHex = String.format("#%06X", 0xFFFFFF and getColor(R.color.colorMarkerSelected))

        //Se configuras y se pinta el area, ademas se elimina un dibujo si ya existe
        polygonAnnotationManager.deleteAll()
        polygonAnnotationManager.create(
            PolygonAnnotationOptions()
                .withGeometry(polygon)
                .withFillColor(colorHex)
                .withFillOpacity(0.45)
        )

        //Retorna el area total en KM2
        return TurfMeasurement.area(polygon) / 1_000_000.0
    }
}
