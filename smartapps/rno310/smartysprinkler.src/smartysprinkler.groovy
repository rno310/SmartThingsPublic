/**
 *  SmartySprinkler
 *
 *  Copyright 2016 Arnaud Benahmed
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
definition(
    name: "SmartySprinkler",
    namespace: "rno310",
    author: "Arnaud Benahmed",
    description: "SmartySprinklery",
    category: "",
    version: "0.1",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")

preferences {
    page(name: "schedulePage", title: "Schedule", nextPage: "sprinklerPage", uninstall: true) {
      	section("App configuration...") {
        	label title: "Choose a title for App", required: true, defaultValue: "Irrigation Scheduler"
        }
	
        section("Water when...") {
            input name: "waterTimeOne",  type: "time", required: true, title: "Turn them on at..."
            input name: "waterTimeTwo",  type: "time", required: false, title: "and again at..."
            input name: "waterTimeThree",  type: "time", required: false, title: "and again at..."
        }
        
    }

    page(name: "sprinklerPage", title: "Sprinkler Controller Setup", install: true) {
        section("Sprinkler switches...") {
            input "switches", "capability.switch", multiple: true
        }
        section("Zone Times...") {
            input "zone1", "string", title: "Fountain Time", description: "minutes", multiple: false, required: false
            input "zone2", "string", title: "Back Left Time", description: "minutes", multiple: false, required: false
            input "zone3", "string", title: "Back Right Time", description: "minutes", multiple: false, required: false
            input "zone4", "string", title: "Rose Alley Time", description: "minutes", multiple: false, required: false
            input "zone5", "string", title: "Front Flower Time", description: "minutes", multiple: false, required: false
            input "zone6", "string", title: "Grass 1 Time", description: "minutes", multiple: false, required: false
            input "zone7", "string", title: "Front Flower 2 Time", description: "minutes", multiple: false, required: false
            input "zone8", "string", title: "Grass 2 Time", description: "minutes", multiple: false, required: false
            input "zone9", "string", title: "Entrance Time", description: "minutes", multiple: false, required: false
        }
        section("Irrigation days"){
        	input "Monday","bool", title: "Monday", required:true, default:true
            input "Tuesday","bool", title: "Tuesday", required:true, default:true
            input "Wednesday","bool", title: "Wednesday", required:true, default:true
            input "Thursday","bool", title: "Thursday", required:true, default:true
            input "Friday","bool", title: "Friday", required:true, default:true
            input "Saturday","bool", title: "Saturday", required:true, default:true
            input "Sunday","bool", title: "Sunday", required:true, default:true
        }
        section("Zip code to check weather...") {
            input "zipcode", "text", title: "Zipcode?", required: false
        }
        section("Skip watering if more than... (default 0.5)") {
            input "wetThreshold", "decimal", title: "Inches?", required: false
        }
        section("Optional: Use this virtual scheduler device...") {
            input "schedulerVirtualDevice", "capability.actuator", required: false
        }
        section ("GroveStreams Feed PUT API key...") {
                input "channelKey", "text", title: "API key", required: false
        }
    }
}

def installed() {
    log.debug "Installed: $settings"
    scheduling()
    state.daysSinceLastWatering = [0,0,0]
    unsubscribe()
    subscribe(app, appTouch)
    
}

def updated() {
    log.debug "Updated: $settings"
    unsubscribe()
    unschedule()
    scheduling()
    state.daysSinceLastWatering = [0,0,0]
    subscribe(app, appTouch)
    
}

// Scheduling
def scheduling() {
	log.debug("I am scheduling with settings: ${settings}")
    schedule(waterTimeOne, "waterTimeOneStart")
    if (waterTimeTwo) {
        schedule(waterTimeTwo, "waterTimeTwoStart")
    }
    if (waterTimeThree) {
        schedule(waterTimeThree, "waterTimeThreeStart")
    }
    
}

def waterTimeOneStart() {
	log.debug("Starting watering")
    state.currentTimerIx = 0
    water_ladwp()
}
def waterTimeTwoStart() {
    state.currentTimerIx = 1
    water_ladwp()
}
def waterTimeThreeStart() {
    state.currentTimerIx = 2
    water_ladwp()
}

def scheduleCheck() {
    log.debug("Schedule check")

    def schedulerState = "noEffect"
    if (schedulerVirtualDevice) {
        schedulerState = schedulerVirtualDevice.latestValue("effect")
    }

    if (schedulerState == "onHold") {
        log.info("Sprinkler schedule on hold.")
        return
    } else {
        schedulerVirtualDevice?.noEffect()
    }

    // Change to rain delay if wet
    schedulerState = isRainDelay() ? "delay" : schedulerState

    if (schedulerState != "delay") {
        state.daysSinceLastWatering[state.currentTimerIx] = daysSince() + 1
    }

    log.debug("Schedule effect $schedulerState. Days since last watering ${daysSince()}. Is watering day? ${isWateringDay()}. Enought time? ${enoughTimeElapsed(schedulerState)} ")

    if ((isWateringDay() && enoughTimeElapsed(schedulerState) && schedulerState != "delay") || schedulerState == "expedite") {
        def w_idx = get_daily_watering_index()
        sendPush("Watering now with index of ${w_idx}")
        state.daysSinceLastWatering[state.currentTimerIx] = 0
        water(w_idx)
        // Assuming that sprinklers will turn themselves off. Should add a delayed off?
    }
    if (!isWateringDay()){
    	sendPush("Skip watering for today.")
        }
        
}


def water_ladwp(){
	def w_idx = 85.0
    try{
    	w_idx = get_daily_watering_index()
    } catch (all){
    	sendPush("ERROR: Watering now with default index of ${w_idx}")
    }
    LogWateringEvent(0,watering_index)
    
    state.daysSinceLastWatering[state.currentTimerIx] = 0
    if (isWateringDay()){
    	sendPush("Watering now with index of ${w_idx}")
    	water(w_idx)
    }
    else {
    log.debug("Today is not a watering day")
    	sendPush("Skip watering for today.")
    }
    
}


def LogWateringEvent(zone_num,water_idx) {
	try{
    	if (channelKey?.trim()){
        def compId = "Watering"
        def streamId = "zone${zone_num}"
        def value = water_idx
       
        log.debug "Logging to yeah GroveStreams ${compId}, ${streamId} = ${value}"
       
        def url = "https://grovestreams.com/api/feed?api_key=${channelKey}&compId=${compId}&${streamId}=${value}"
        def putParams = [
                uri: url,
                body: []]
 
        httpPut(putParams) { response ->
                if (response.status != 200 ) {
                        log.debug "GroveStreams logging failed, status = ${response.status}"
                }
        }
	}
    }
    catch (all){
    	log.debug("Could not log to Grovestream.")
        }
}

def get_daily_watering_index(){
	def url = "http://www.bewaterwise.com/RSS/rsswi.xml"

	def rss = new XmlSlurper().parse(url) 

	def watering_index = 100
	log.debug rss.channel.title
	rss.channel.item.each {
    	if (it.title == "Daily Watering Index"){
        	watering_index = it.description.toDouble()
            }
	}
    log.debug("The daily watering index is ${watering_index}")
   
    return watering_index
}

def isWateringDay() {
	def calendar = Calendar.getInstance()
	calendar.setTimeZone(location.timeZone)
	def today = calendar.get(Calendar.DAY_OF_WEEK)
    if ((today ==  Calendar.MONDAY) && settings["Monday"]){ return true }
    if ((today ==  Calendar.TUESDAY) && settings["Tuesday"]){ return true }
    if ((today ==  Calendar.WEDNESDAY) && settings["Wednesday"]){ return true }
    if ((today ==  Calendar.THURSDAY) && settings["Thursday"]){ return true }
    if ((today ==  Calendar.FRIDAY) && settings["Friday"]){ return true }
    if ((today ==  Calendar.SATURDAY) && settings["Saturday"]){ return true }
    if ((today ==  Calendar.SUNDAY) && settings["Sunday"]){ return true }
    return false
}

def enoughTimeElapsed(schedulerState) {
    return true
}

def daysSince() {
    if(!state.daysSinceLastWatering) state.daysSinceLastWatering = [0,0,0]
    state.daysSinceLastWatering[state.currentTimerIx] ?: 0
}

def isRainDelay() { 
    def rainGauge = wasWetYesterday()+isWet()+isStormy()
    log.info ("Rain gauge reads $rainGauge in")
    if (rainGauge > (wetThreshold?.toFloat() ?: 0.5)) {
        log.trace "Watering is rain delayed"
        sendPush("Skipping watering today due to precipitation.")
        for(s in switches) {
            if("rainDelayed" in s.supportedCommands.collect { it.name }) {
                s.rainDelayed()
            }
        }
        if("rainDelayed" in schedulerVirtualDevice?.supportedCommands.collect { it.name }) {
            schedulerVirtualDevice.rainDelayed()
        }
        return true
    }
    return false
}

def safeToFloat(value) {
    if(value && value.isFloat()) return value.toFloat()
    return 0.0
}

def wasWetYesterday() {
    if (!zipcode) return false

    def yesterdaysWeather = getWeatherFeature("yesterday", zipcode)
    def yesterdaysPrecip=yesterdaysWeather.history.dailysummary.precipi.toArray()
    def yesterdaysInches=safeToFloat(yesterdaysPrecip[0])
    log.info("Checking yesterdays percipitation for $zipcode: $yesterdaysInches in")
	return yesterdaysInches
}


def isWet() {
    if (!zipcode) return false

    def todaysWeather = getWeatherFeature("conditions", zipcode)
    def todaysInches = safeToFloat(todaysWeather.current_observation.precip_today_in)
    log.info("Checking percipitation for $zipcode: $todaysInches in")
    return todaysInches
}

def isStormy() {
    if (!zipcode) return false

    def forecastWeather = getWeatherFeature("forecast", zipcode)
    def forecastPrecip=forecastWeather.forecast.simpleforecast.forecastday.qpf_allday.in.toArray()
    def forecastInches=forecastPrecip[0]
    log.info("Checking forecast percipitation for $zipcode: $forecastInches in")
    return forecastInches
}

def water(w_idx) {
    state.triggered = true
    log.info('Watering in water function')
    //if(anyZoneTimes()) {
        def zoneTimes = []
        log.info("Watering now with index of ${w_idx}")
        for(int z = 1; z <= 9; z++) {
            
            def zoneTime = safeToFloat(settings["zone${z}"])
            log.info("Turning zone ${z} on base time ${zoneTime}, water index ${w_idx}")
            if(zoneTime) {
            	def  adjTime =  Math.max(10,zoneTime * 60 * w_idx/ 100.0).round()
                
                	LogWateringEvent(z,adjTime/60)
               
                log.info("Zone ${z} on for ${adjTime} seconds (base time ${zoneTime} min, water index ${w_idx})")
            	zoneTimes += "${z}:${adjTime}"
                }
            	
        }
//    }
        
        log.info(zoneTimes.join(","))
        
        switches.OnWithZoneTimes(zoneTimes.join(","))
    //} else {
    //    log.debug("Turning all zones on")
    //    switches.on()
    //}
    L
}

def anyZoneTimes() {
    return zone1 || zone2 || zone3 || zone4 || zone5 || zone6 || zone7 || zone8
}

def appTouch(evt) {
    log.debug "appTouch: $evt"
    water(get_daily_watering_index())
}


