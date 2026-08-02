package Ir.co.tfs.farazaman.data.model

data class RoadData(
    val distance: Double,
    val gisLayersDataId: Int,
    val oneway: String,
    val maxspeed: Int,
    val layer: Int,
    val bridge: String,
    val tunnel: String,
    val gisLayersId: Int,
    val osm_id: String,
    val code: Int,
    val fclass: String,
    val name: String
) 
