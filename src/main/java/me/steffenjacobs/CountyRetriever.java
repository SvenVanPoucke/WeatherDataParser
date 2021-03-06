package me.steffenjacobs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.steffenjacobs.domain.Station;

/**
 * This class can load the weather stations from climatecenter.fsu.edu and
 * parses them using their latitude and longitude through geo.fcc.gov to get the
 * associated county.
 * 
 * @author Steffen Jacobs
 */
public class CountyRetriever {

	private static final Logger LOG = Logger.getLogger(CountyRetriever.class.getSimpleName());

	private static final String WEATHER_URL = "https://climatecenter.fsu.edu/climate-data-access-tools/downloadable-data";
	private static final String AREA_URL = "https://geo.fcc.gov/api/census/area?lat=%s&lon=%s";

	private static final Pattern PAT_LONGLAT = Pattern.compile(
			"var latlng([0-9]*) = new google\\.maps\\.LatLng\\(([0-9]+\\.[0-9]+), ([-]?[0-9]+\\.[0-9]+)\\);\\R*\\s+var marker[0-9]* = new google\\.maps\\.Marker\\(\\{\\R*\\s+position: latlng[0-9]*,\\R*\\s+title:\"([\\w\\s*]+)\"");
	private static final Pattern PAT_JSON_COUNTY = Pattern.compile("\"county_name\":\"([\\w\\.?[-]?\\s*]+)\",");

	public static void main(String[] args) throws Exception {
		getCountyForStation();

	}

	protected static Map<Integer, Station> getCountyForStation() throws IOException {
		final long now = System.currentTimeMillis();
		LOG.log(Level.INFO, "Starting to retrieve counties for stations");
		// retrieve web page from climatecenter
		String htmlString = sendGet(WEATHER_URL);
		Matcher matcher = PAT_LONGLAT.matcher(htmlString);
		List<Station> stations = new ArrayList<>();
		Map<Integer, Station> stationMap = new HashMap<>();
		// retrieve stations from HTML document
		while (matcher.find()) {
			Station station = new Station(Integer.parseInt(matcher.group(1)), Double.parseDouble(matcher.group(2)), Double.parseDouble(matcher.group(3)), matcher.group(4));
			stations.add(station);
			stationMap.put(station.getStationId(), station);
		}

		// retrieve county from geo.fcc.gov via latitude and longitude
		for (Station station : stations) {
			String html = sendGet(String.format(AREA_URL, station.getLat(), station.getLng()));
			Matcher matcher2 = PAT_JSON_COUNTY.matcher(html);
			if (!matcher2.find()) {
				LOG.log(Level.SEVERE, "Error: County for station {0} not found.", station.getName());
			} else {
				station.setCounty(matcher2.group(1));
			}

			LOG.log(Level.FINE, "{0} ({1}): Lat: {2}, Lng: {3}, County: {4}",
					new Object[] { station.getName(), station.getStationId(), station.getLat(), station.getLng(), station.getCounty() });
		}

		LOG.log(Level.INFO, "retrieving counties for {0} stations took {1}ms.\n", new Object[] { stationMap.keySet().size(), (System.currentTimeMillis() - now) });

		return stationMap;
	}

	/**
	 * @return a String containg the httml response of a get request with an
	 *         empty header to <i>url</i>
	 */
	private static String sendGet(String url) throws MalformedURLException, IOException {

		HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();

		BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuilder response = new StringBuilder();

		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();

		return response.toString();
	}
}
