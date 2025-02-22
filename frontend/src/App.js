import React, { useState, useEffect } from 'react';
import mqtt from 'mqtt';
import { MapContainer, TileLayer, Marker, Popup, Rectangle } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

// Function to generate a unique and consistent color based on the client ID
const getColor = (id) => `hsl(${[...id].reduce((acc, char) => acc + char.charCodeAt(0), 0) % 360}, 70%, 50%)`;

// Function to create a custom colored marker icon with a colored pin and white center
const coloredIcon = (color) => L.icon({
  // `L.icon` creates a new Leaflet icon object for markers.
  // The `color` parameter dynamically sets the pin color.
  iconUrl: `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(`
    <svg xmlns='http://www.w3.org/2000/svg' width='25' height='41' viewBox='0 0 25 41'>
      <path d='M12.5 0C5.6 0 0 5.6 0 12.5c0 9.7 12.5 28.5 12.5 28.5S25 22.2 25 12.5C25 5.6 19.4 0 12.5 0z' fill='${color}' stroke='#333' stroke-width='1'/>
      <circle cx='12.5' cy='12.5' r='5' fill='white'/>
    </svg>
  `)}`,
  // `iconUrl`: Defines the icon using an embedded SVG image through a Data URI.
  // `encodeURIComponent`: Encodes the SVG content to safely include it as a URL.
  // Inside the SVG:
  // - `<svg>`: Creates a scalable vector graphic with dimensions 25x41 pixels.
  // - `<path>`: Draws the pin shape using path commands in the `d` attribute.
  //    * `fill='${color}'`: Dynamically fills the pin with the provided color.
  //    * `stroke='#333'`: Adds a dark gray border for better contrast.
  //    * `stroke-width='1'`: Sets the border width to 1 pixel.
  // - `<circle>`: Draws a white circle at the center of the pin.
  //    * `cx='12.5' cy='12.5'`: Positions the circle at the pin's center.
  //    * `r='5'`: Sets the circle’s radius to 5 pixels.
  //    * `fill='white'`: Makes the circle white for contrast against the pin color.
  iconSize: [25, 41],
  iconAnchor: [12.5, 41],
  popupAnchor: [0, -35],
});

const BROKER_URL = 'ws://broker.emqx.io:8083/mqtt';
const TOPIC = 'gps/clients';
const DEFAULT_POSITION = [37.4, -122.2];

// Grid settings
const BOARD_SIZE = 8; // 8x8 grid
const MIN_LAT = 37.0; // Southern boundary latitude
const MAX_LAT = 37.8; // Northern boundary latitude
const MIN_LON = -122.5; // Western boundary longitude
const MAX_LON = -121.7; // Eastern boundary longitude

const App = () => {
  const [positions, setPositions] = useState([]); // State to store the positions of all clients

  useEffect(() => {
    const client = mqtt.connect(BROKER_URL); // Establish a connection to the MQTT broker

    client.on('connect', () => {
      console.log('Connected to MQTT broker'); // Log a message when connected to the broker
      client.subscribe(TOPIC, (err) => { // Subscribe to the topic
        if (err) console.error('Subscription error:', err);
      });
    });

    client.on('message', (_, msg) => { // Handle incoming messages
      const [id, lat, lon] = msg.toString().split(','); // Parse incoming message (expected format: "id,lat,lon")
      if (!id || !lat || !lon) return;

      // Update positions state with new or updated client position
      setPositions((prev) => [
        ...prev.filter((pos) => pos.id !== id), // Remove any existing entry for the same client ID
        { id, lat: parseFloat(lat), lon: parseFloat(lon) }, // Add new position data
      ]);
    });

    return () => client.end(); // Clean up the MQTT connection when the component unmounts
  }, []);

  // Render an 8x8 grid overlay on the map
  const renderBoardGrid = () => {
    const grid = [];
    const latStep = (MAX_LAT - MIN_LAT) / BOARD_SIZE; // Latitude interval between grid lines
    const lonStep = (MAX_LON - MIN_LON) / BOARD_SIZE; // Longitude interval between grid lines

    for (let row = 0; row < BOARD_SIZE; row++) {
      for (let col = 0; col < BOARD_SIZE; col++) {
        const bounds = [
          [MIN_LAT + row * latStep, MIN_LON + col * lonStep], // Bottom-left corner
          [MIN_LAT + (row + 1) * latStep, MIN_LON + (col + 1) * lonStep], // Top-right corner
        ];

        grid.push(
          <Rectangle
            key={`cell-${row}-${col}`} // Unique key for each grid cell
            bounds={bounds} // Position bounds for the rectangle
            pathOptions={{ color: 'gray', weight: 1, fillOpacity: 0 }}
          />
        );
      }
    }

    return grid;
  };

  return (
    <div style={{ height: '100vh' }}> {/* Full-page height container */}
      <h2 style={{ textAlign: 'center' }}>Real-Time Client Positions with Unique Marker Colors and 8x8 Grid</h2>
      <MapContainer 
        center={DEFAULT_POSITION}
        zoom={11} // Initial zoom level
        scrollWheelZoom={true} // Enable zooming with the scroll wheel
        style={{ height: '90vh' }}
        maxBounds={[[MIN_LAT, MIN_LON], [MAX_LAT, MAX_LON]]}
      >
      {/* TileLayer fetches and displays map tiles from OpenStreetMap*/}
        <TileLayer
          attribution="&copy; <a href='https://www.openstreetmap.org/copyright'>OpenStreetMap</a> contributors"
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />

        {/* Render the 8x8 board grid */}
        {renderBoardGrid()}

        {/* Render client markers with unique colors */}
        {positions.map(({ id, lat, lon }) => (
          <Marker key={id} position={[lat, lon]} icon={coloredIcon(getColor(id))}> {/* Custom colored marker icon */}
            <Popup>
              <strong>{id}</strong><br />
              Lat: {lat.toFixed(4)}<br />
              Lon: {lon.toFixed(4)}
            </Popup>
          </Marker>
        ))}
      </MapContainer>
    </div>
  );
};

export default App;
