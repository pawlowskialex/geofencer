package model

/**
 * Created by alex on 12/15/14.
 */
sealed case class Facility(name: String, fence: Geofence, exits: List[Point])