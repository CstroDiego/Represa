package mx.itson.represa

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import cn.pedant.SweetAlert.SweetAlertDialog
import com.ingenieriajhr.blujhr.BluJhr
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import mx.itson.represa.databinding.ActivityMainBinding
import org.json.JSONException
import org.json.JSONObject
import java.util.Timer
import java.util.TimerTask


class MainActivity : AppCompatActivity() {
    //bluetooth var
    lateinit var blue: BluJhr
    var devicesBluetooth = ArrayList<String>()

    //visible ListView
    var graphviewVisible = true

    //graphviewSeries
    lateinit var sensor: LineGraphSeries<DataPoint?>
    lateinit var humedad: LineGraphSeries<DataPoint>

    //nos indica si estamos recibiendo datos o no
    var initGraph = false

    //nos almacena el estado actual de la conexion bluetooth
    var stateConn = BluJhr.Connected.False

    //valor que se suma al eje x despues de cada actualizacion
    var ejeX = 0.5

    //sweet alert necesarios
    lateinit var loadSweet: SweetAlertDialog
    lateinit var errorSweet: SweetAlertDialog
    lateinit var okSweet: SweetAlertDialog
    lateinit var disconnection: SweetAlertDialog

    private lateinit var binding: ActivityMainBinding
    private val REQUEST_ENABLE_BLUETOOTH = 1

    private var timer: Timer? = null
    private var timerTask: TimerTask? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        // graphview
        initGraph()

        // init var sweetAlert
        initSweet()

        blue = BluJhr(this)
        blue.onBluetooth()

        // *****************************************************************************************
        blue.initializeBluetooth()
        // *****************************************************************************************

        binding.btnViewDevice.setOnClickListener {
            when (graphviewVisible) {
                false -> invisibleListDevice()
                true -> visibleListDevice()
            }
            binding.btnDisconnect.setOnClickListener {
                blue.bluTx("4")
                blue.closeConnection()
            }

        }

        binding.listDeviceBluetooth.setOnItemClickListener { adapterView, view, i, l ->
            if (devicesBluetooth.isNotEmpty()) {
                blue.connect(devicesBluetooth[i])
                initSweet()
                blue.setDataLoadFinishedListener(object : BluJhr.ConnectedBluetooth {
                    override fun onConnectState(state: BluJhr.Connected) {
                        stateConn = state
                        when (state) {
                            BluJhr.Connected.True -> {
                                loadSweet.dismiss()
                                okSweet.show()
                                invisibleListDevice()
                                rxReceived()
                                binding.btnDisconnect.visibility = View.VISIBLE
                                timer?.cancel()
                                timer = Timer()
                                timerTask = object : TimerTask() {
                                    override fun run() {
                                        blue.bluTx("3")
                                    }
                                }
                                timer?.schedule(timerTask, 0, 1000)
                            }

                            BluJhr.Connected.Pending -> {
                                loadSweet.show()
                            }

                            BluJhr.Connected.False -> {
                                loadSweet.dismiss()
                                errorSweet.show()
                            }

                            BluJhr.Connected.Disconnect -> {
                                loadSweet.dismiss()
                                disconnection.show()
                                visibleListDevice()
                                binding.btnDisconnect.visibility = View.GONE
                                timer?.cancel()
                                timerTask?.cancel()
                            }
                        }
                    }
                })
            }
        }
        binding.btnInitStop.setOnClickListener {
            if (stateConn == BluJhr.Connected.True) {
                initGraph = when (initGraph) {
                    true -> {
                        blue.bluTx("2")
                        binding.btnInitStop.text = "INICIAR"
                        false
                    }

                    false -> {
                        blue.bluTx("1")
                        binding.btnInitStop.text = "DETENER"
                        true
                    }
                }
            }
        }
    }

    // *********************************************************************************************
    private fun rxReceived() {
        blue.loadDateRx(object : BluJhr.ReceivedData {
            override fun rxDate(rx: String) {
                try {
                    val jsonObject = JSONObject(rx)
                    val dataArray = jsonObject.getJSONArray("data")
                    //val name = jsonObject.getString("sensor")
                    for (i in 0 until dataArray.length()) {
                        ejeX += 1
                        //binding.txtSen.text = name.toString()
                        sensor.appendData(DataPoint(ejeX, dataArray.getDouble(i)), true, 400)
                    }
                } catch (e: JSONException) {
                    Log.e("JSON", e.toString())

                }
            }
        })
    }
    // *********************************************************************************************

    private fun initSweet() {
        loadSweet = SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE)
        okSweet = SweetAlertDialog(this, SweetAlertDialog.SUCCESS_TYPE)
        errorSweet = SweetAlertDialog(this, SweetAlertDialog.ERROR_TYPE)
        disconnection = SweetAlertDialog(this, SweetAlertDialog.NORMAL_TYPE)

        loadSweet.titleText = "Conectando"
        loadSweet.setCancelable(false)
        errorSweet.titleText = "Algo salio mal"
        okSweet.titleText = "Conectado"
        disconnection.titleText = "Desconectado"
    }

    private fun initGraph() {
        binding.graph.viewport.isXAxisBoundsManual = true
        binding.graph.viewport.isYAxisBoundsManual = false
        binding.graph.viewport.setMinX(0.0)
        binding.graph.viewport.setMaxX(250.0)
        binding.graph.viewport.setMaxY(1024.0)
        binding.graph.viewport.setMinY(0.0)

        binding.graph.viewport.isScalable = true
        binding.graph.viewport.setScalableY(true)

        sensor = LineGraphSeries()
        sensor.isDrawDataPoints = true
        sensor.isDrawBackground = true
        sensor.color = Color.RED

        binding.graph.addSeries(sensor)
        //  binding.graph.addSeries(humedad)
    }

    private fun invisibleListDevice() {
        binding.containerGraph.visibility = View.VISIBLE
        binding.containerDevice.visibility = View.GONE
        graphviewVisible = true
        binding.btnViewDevice.text = "DISPOSITIVOS"
    }

    private fun visibleListDevice() {
        binding.containerGraph.visibility = View.GONE
        binding.containerDevice.visibility = View.VISIBLE
        graphviewVisible = false
        binding.btnViewDevice.text = "GRAFICA"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!blue.stateBluetoooth() && requestCode == 100) {
            blue.initializeBluetooth()
        } else {
            if (requestCode == 100) {
                devicesBluetooth = blue.deviceBluetooth()
                if (devicesBluetooth.isNotEmpty()) {
                    val adapter = ArrayAdapter(
                        this,
                        android.R.layout.simple_expandable_list_item_1,
                        devicesBluetooth
                    )
                    binding.listDeviceBluetooth.adapter = adapter
                } else {
                    Toast.makeText(this, "No tienes vinculados dispositivos", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}