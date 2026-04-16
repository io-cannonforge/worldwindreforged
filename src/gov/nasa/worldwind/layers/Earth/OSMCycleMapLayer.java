/*
 * Copyright 2006-2009, 2017, 2020 United States Government, as represented by the
 * Administrator of the National Aeronautics and Space Administration.
 * All rights reserved.
 *
 * The NASA World Wind Java (WWJ) platform is licensed under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * NASA World Wind Java (WWJ) also contains the following 3rd party Open Source
 * software:
 *
 *     Jackson Parser – Licensed under Apache 2.0
 *     GDAL – Licensed under MIT
 *     JOGL – Licensed under  Berkeley Software Distribution (BSD)
 *     Gluegen – Licensed under Berkeley Software Distribution (BSD)
 *
 * A complete listing of 3rd Party software notices and licenses included in
 * NASA World Wind Java (WWJ)  can be found in the WorldWindJava-v2.2 3rd-party
 * notices and licenses PDF found in code directory.
 */
package gov.nasa.worldwind.layers.Earth;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.avlist.AVList;
import gov.nasa.worldwind.avlist.AVListImpl;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.layers.mercator.BasicMercatorTiledImageLayer;
import gov.nasa.worldwind.layers.mercator.MercatorSector;
import gov.nasa.worldwind.util.LevelSet;
import gov.nasa.worldwind.util.Tile;
import gov.nasa.worldwind.util.TileUrlBuilder;

/**
 * @version $Id: OSMCycleMapLayer.java 1171 2013-02-11 21:45:02Z dcollins $
 */
public class OSMCycleMapLayer extends BasicMercatorTiledImageLayer
{
	public OSMCycleMapLayer()
	{
		super(makeLevels());
    }

	private static LevelSet makeLevels()
	{
		AVList params = new AVListImpl();

		params.setValue(AVKey.TILE_WIDTH, 256);
		params.setValue(AVKey.TILE_HEIGHT, 256);
		params.setValue(AVKey.DATA_CACHE_NAME, "Earth/OSM-Mercator/OpenStreetMap Cycle");
		params.setValue(AVKey.SERVICE, "http://b.andy.sandbox.cloudmade.com/tiles/cycle/");
		params.setValue(AVKey.DATASET_NAME, "*");
		params.setValue(AVKey.FORMAT_SUFFIX, ".png");
		params.setValue(AVKey.NUM_LEVELS, 16);
		params.setValue(AVKey.NUM_EMPTY_LEVELS, 0);
		params.setValue(AVKey.LEVEL_ZERO_TILE_DELTA, new LatLon(Angle
				.fromDegrees(22.5d), Angle.fromDegrees(45d)));
		params.setValue(AVKey.SECTOR, new MercatorSector(-1.0, 1.0,
				Angle.NEG180, Angle.POS180));
		params.setValue(AVKey.TILE_URL_BUILDER, new URLBuilder());

		return new LevelSet(params);
	}

	private static class URLBuilder implements TileUrlBuilder
	{
		@Override
		public URL getURL(Tile tile, String imageFormat)
				throws MalformedURLException
		{
			return URI.create(tile.getLevel().getService()
					+ (tile.getLevelNumber() + 3) +"/"+ tile.getColumn()+"/"+ ((1 << (tile.getLevelNumber()) + 3) - 1 - tile.getRow()) + ".png").toURL();
		}
	}

	@Override
	public String toString()
	{
		return "OpenStreetMap Cycle";
	}
}
