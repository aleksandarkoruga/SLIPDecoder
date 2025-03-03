SLIPDecoder {
	var deviceName, <>rate, <>port, decode, <>actions, <>addresses, <numAddresses;

	*new {|deviceName="/dev/cu.SLAB_USBtoUART", rate=9600, numAddresses=3|

		^super.new.initSLIPDecoder(deviceName, rate, numAddresses);
	}

	initSLIPDecoder {|a,b,c|
		deviceName = a;
		rate = b;
		numAddresses = c;
		port = SerialPort(deviceName, rate);
		actions = {}!numAddresses; //second byte in the data array is its address if it's properly configured in OSC. THIS MIGHT BE DIFFERENT FOR DIFFERENT VERSIONS OF THE OSC PROTOCOL. This value is used to select between a number of actions which can be externally set. Each action is a function that takes the message contents as an argument. see 'decode' below.
		decode = {|data| //function for decoding the properly-SLIP-decoded message.
			var temp = 0!15, address,endNames, output;
			address=data.findAll(Int8Array[47])+1;
			endNames=address.collect({|item|data.find(Int8Array[0],item)});
			address=endNames.collect({|item,i|   (data[ (address[i]) .. (item-1)]).collect({|it| it.asAscii.asString.asInteger}).convertDigits()   });
			output = (data[data.size-2].asBinaryDigits.at([6,7]) ++ data[data.size-1].asBinaryDigits).convertDigits(2);
			actions[address[0]].value([address,output]);
		}
	}

	stopSerial {
		port.close();

	}

	start {
		fork {
			// SLIP DECODING
			// ===================
			//
			// The packets are SLIP encoded using these special characters:
			// end = 8r300 (2r11000000 or 0xc0 or 192)
			// esc = 8r333 (2r11011011 or 0xdb or 219)
			// esc_end = 8r334 (2r011011100 or 0xdc or 220)
			// esc_esc = 8r335 (2r011011101 or 0xdd or 221)
			// original code by Martin Marier & Fredrik Olofsson

			var buffer, serialByte;
			var maxPacketSize = 64; // 16 is enouch for 8 10 bit values.
			var slipEND = 8r300;
			var slipESC = 8r333;
			var slipESC_END = 8r334;
			var slipESC_ESC = 8r335;
			buffer = Int8Array(maxSize:maxPacketSize);
			{
				serialByte = port.read;
				serialByte.switch(
					slipEND, {
						if(buffer.size()!=0,{
						decode.value(buffer);
						buffer = Int8Array(maxSize:maxPacketSize);});
					},
					slipESC, {
						serialByte = port.read;
						serialByte.switch(
							slipESC_END, { buffer.add(slipEND) },
							slipESC_ESC, { buffer.add(slipESC) },
							{"SLIP encoding error.".error }
						)
					},
					{ buffer.add(serialByte) }
				);
			}.loop
		}



	}

}
