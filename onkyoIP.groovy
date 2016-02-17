/**
 *  Onkyo IP Control Device Type for SmartThings
 *  Allan Klein (@allanak)
 *  Originally based on: Mike Maxwell's code
 *
 *  Usage:
 *  1. Be sure you have enabled control of the receiver via the network under the settings on your receiver.
 *  2. Add this code as a device handler in the SmartThings IDE
 *  3. Create a device using OnkyoIP as the device handler using a hexadecimal representation of IP:port as the device network ID value
 *  For example, a receiver at 192.168.1.222:60128 would have a device network ID of C0A801DE:EAE0
 *  Note: Port 60128 is the default Onkyo eISCP port so you shouldn't need to change anything after the colon
 *  4. Enjoy the new functionality of the SmartThings app
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
 * Some future commands that might be useful to incorporate reading:
 * Power Status: PWRQSTN
 * Input Selected: SLIQSTN
 * Volume Level: MVLQSTN (value in hex)
 * Artist Name: NATQSTN
 * Track Name: NTIQSTN
 * Zone 2 Mute: ZMTQSTN
 * Zone 2 Volume: ZVLQSTN
 * Zone 2 Input Selected: SLZQSTN
 * ISCP commands were found at https://github.com/miracle2k/onkyo-eiscp/blob/master/eiscp-commands.yaml
 */

metadata {
	definition (name: "onkyoIP", namespace: "allanak", author: "Allan Klein") {
	capability "Switch"
	capability "Music Player"
	command "cable"
	command "stb"
	command "pc"
	command "net"
	command "aux"
	command "z2on"
	command "z2off"
	command "makeNetworkId", ["string","string"]
	}

simulator {
		// TODO: define status and reply messages here
	}

tiles {
	standardTile("switch", "device.switch", width: 1, height: 1, canChangeIcon: true) {
        	state "on", label: '${name}', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#79b821"
        	state "off", label: '${name}', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff"
   		}
        standardTile("mute", "device.switch", inactiveLabel: false, decoration: "flat") {
		state "unmuted", label:"mute", action:"mute", icon:"st.custom.sonos.unmuted", backgroundColor:"#ffffff", nextState:"muted"
		state "muted", label:"unmute", action:"unmute", icon:"st.custom.sonos.muted", backgroundColor:"#ffffff", nextState:"unmuted"
        	}
        standardTile("cable", "device.switch", decoration: "flat"){
        	state "cable", label: 'cable', action: "cable", icon:"st.Electronics.electronics3"
        	}
        standardTile("stb", "device.switch", decoration: "flat"){
        	state "stb", label: 'shield', action: "stb", icon:"st.Electronics.electronics5"
        	}
        standardTile("pc", "device.switch", decoration: "flat"){
        	state "pc", label: 'pc', action: "pc", icon:"st.Electronics.electronics18"
        	}
        standardTile("net", "device.switch", decoration: "flat"){
        	state "net", label: 'net', action: "net", icon:"st.Electronics.electronics2"
        	}
        standardTile("aux", "device.switch", decoration: "flat"){
        	state "aux", label: 'aux', action: "aux", icon:"st.Electronics.electronics6"
        	}
	controlTile("levelSliderControl", "device.level", "slider", height: 1, width: 2, inactiveLabel: false, range:"(0..70)") {
		state "level", label:'${currentValue}', action:"setLevel", backgroundColor:"#ffffff"
		}
        standardTile("zone2", "device.switch", inactiveLabel: false, decoration: "flat") {
		state "off", label:"Enable Zone 2", action:"z2on", icon:"st.custom.sonos.unmuted", backgroundColor:"#ffffff", nextState:"on"
		state "on", label:"Disable Zone 2", action:"z2off", icon:"st.custom.sonos.muted", backgroundColor:"#ffffff", nextState:"off"
        	}
        /*   Commenting this out as it doesn't work yet     
        valueTile("currentSong", "device.trackDescription", inactiveLabel: true, height:1, width:3, decoration: "flat") {
		state "default", label:'${currentValue}', backgroundColor:"#ffffff"
		}
	*/
	}

	
    main "switch"
    details(["switch","mute","cable","stb","pc","net","aux","levelSliderControl","zone2"])
    //Add currentSong to above once I can figure out how to get the QSTN commands parsed into artist/song titles
}

// parse events into attributes
def parse(description) {
    def msg = parseLanMessage(description)
    def headersAsString = msg.header // => headers as a string
    def headerMap = msg.headers      // => headers as a Map
    def body = msg.body              // => request body as a string
    def status = msg.status          // => http status code of the response
    def json = msg.json              // => any JSON included in response body, as a data structure of lists and maps
    def xml = msg.xml                // => any XML included in response body, as a document tree structure
    def data = msg.data              // => either JSON or XML in response body (whichever is specified by content-type header in response)
}
//device.deviceNetworkId should be writeable now..., and its not...
def makeNetworkId(ipaddr, port) { 
	String hexIp = ipaddr.tokenize('.').collect {String.format('%02X', it.toInteger()) }.join() 
	String hexPort = String.format('%04X', port.toInteger()) 
	log.debug "The target device is configured as: ${hexIp}:${hexPort}" 
	return "${hexIp}:${hexPort}" 
	}
def updated() {
	//device.deviceNetworkId = makeNetworkId(settings.deviceIP,settings.devicePort)	
	}
def mute(){
	log.debug "Muting receiver"
	sendEvent(name: "switch", value: "muted")
	def msg = getEiscpMessage("AMT01")
	def ha = new physicalgraph.device.HubAction(msg,physicalgraph.device.Protocol.LAN )
	return ha
	}

def unmute(){
	log.debug "Unmuting receiver"
	sendEvent(name: "switch", value: "unmuted")
	def msg = getEiscpMessage("AMT00")
	def ha = new physicalgraph.device.HubAction(msg,physicalgraph.device.Protocol.LAN )
	return ha
	}

def setLevel(vol){
	log.debug "Setting volume level $vol"
	if (vol < 0) vol = 0
	else if( vol > 70) vol = 70
	else {
		sendEvent(name:"setLevel", value: vol)
		String volhex = vol.bytes.encodeHex()
		// Strip the first six zeroes of the hex encoded value because we send volume as 2 digit hex
		volhex = volhex.replaceFirst("\\u0030{6}","")
		log.debug "Converted volume $vol into hex: $volhex"
		def msg = getEiscpMessage("MVL${volhex}")
		log.debug "Setting volume to MVL${volhex}"
		def ha = new physicalgraph.device.HubAction(msg,physicalgraph.device.Protocol.LAN )
		return ha
		}
	}

def on() {
	log.debug "Powering on receiver"
	sendEvent(name: "switch", value: "on")
	def msg = getEiscpMessage("PWR01")
	def ha = new physicalgraph.device.HubAction(msg,physicalgraph.device.Protocol.LAN)
	return ha
	}

def off() {
	log.debug "Powering off receiver"
	sendEvent(name: "switch", value: "off")
	def msg = getEiscpMessage("PWR00")
	def ha = new physicalgraph.device.HubAction(msg,physicalgraph.device.Protocol.LAN)
	return ha
	}

def cable() {
	log.debug "Setting input to Cable"
	def msg = getEiscpMessage("SLI01")
	def ha = new physicalgraph.device.HubAction(msg,physicalgraph.device.Protocol.LAN)
	return ha
	}

def stb() {
	log.debug "Setting input to STB"
	def msg = getEiscpMessage("SLI02")
	def ha = new physicalgraph.device.HubAction(msg,physicalgraph.device.Protocol.LAN)
	return ha
	}

def pc() {
	log.debug "Setting input to PC"
	def msg = getEiscpMessage("SLI05")
	def ha = new physicalgraph.device.HubAction(msg,physicalgraph.device.Protocol.LAN)
	return ha
	}

def net() {
	log.debug "Setting input to NET"
	def msg = getEiscpMessage("SLI2B")
	def ha = new physicalgraph.device.HubAction(msg,physicalgraph.device.Protocol.LAN)
	log.debug "Pressing play"
	def msg2 = getEiscpMessage("NSTpxx")
	def ha2 = new physicalgraph.device.HubAction(msg2,physicalgraph.device.Protocol.LAN)    
	return ha
    return ha2
	}

def aux() {
	log.debug "Setting input to AUX"
	def msg = getEiscpMessage("SLI03")
	def ha = new physicalgraph.device.HubAction(msg,physicalgraph.device.Protocol.LAN)
	return ha
	}
def z2on() {
	log.debug "Turning on Zone 2"
	def msg = getEiscpMessage("ZPW01")
	def ha = new physicalgraph.device.HubAction(msg,physicalgraph.device.Protocol.LAN)
	return ha
	}
def z2off() {
	log.debug "Turning off Zone 2"
	def msg = getEiscpMessage("ZPW00")
	def ha = new physicalgraph.device.HubAction(msg,physicalgraph.device.Protocol.LAN)
	return ha
	}


def getEiscpMessage(command){
	def sb = StringBuilder.newInstance()
	def eiscpDataSize = command.length() + 3  // this is the eISCP data size
	def eiscpMsgSize = eiscpDataSize + 1 + 16  // this is the size of the entire eISCP msg

	/* This is where I construct the entire message
        character by character. Each char is represented by a 2 disgit hex value */
	sb.append("ISCP")
	// the following are all in HEX representing one char

	// 4 char Big Endian Header
	sb.append((char)Integer.parseInt("00", 16))
	sb.append((char)Integer.parseInt("00", 16))
	sb.append((char)Integer.parseInt("00", 16))
	sb.append((char)Integer.parseInt("10", 16))

	// 4 char  Big Endian data size
	sb.append((char)Integer.parseInt("00", 16))
	sb.append((char)Integer.parseInt("00", 16))
	sb.append((char)Integer.parseInt("00", 16))
	// the official ISCP docs say this is supposed to be just the data size  (eiscpDataSize)
	// ** BUT **
	// It only works if you send the size of the entire Message size (eiscpMsgSize)
	// Changing eiscpMsgSize to eiscpDataSize for testing
	sb.append((char)Integer.parseInt(Integer.toHexString(eiscpDataSize), 16))
	//sb.append((char)Integer.parseInt(Integer.toHexString(eiscpMsgSize), 16))


	// eiscp_version = "01";
	sb.append((char)Integer.parseInt("01", 16))

	// 3 chars reserved = "00"+"00"+"00";
	sb.append((char)Integer.parseInt("00", 16))
	sb.append((char)Integer.parseInt("00", 16))
	sb.append((char)Integer.parseInt("00", 16))

	//  eISCP data
	// Start Character
	sb.append("!")

	// eISCP data - unittype char '1' is receiver
	sb.append("1")

	// eISCP data - 3 char command and param    ie PWR01
	sb.append(command)

	// msg end - this can be a few different cahrs depending on you receiver
	// my NR5008 works when I use  'EOF'
	//OD is CR
	//0A is LF
	/*
	[CR]			Carriage Return					ASCII Code 0x0D			
	[LF]			Line Feed						ASCII Code 0x0A			
	[EOF]			End of File						ASCII Code 0x1A			
	*/
	//works with cr or crlf
	sb.append((char)Integer.parseInt("0D", 16)) //cr
	//sb.append((char)Integer.parseInt("0A", 16))

	return sb.toString()
	}
