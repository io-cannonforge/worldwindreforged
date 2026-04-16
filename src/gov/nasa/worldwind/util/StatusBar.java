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
package gov.nasa.worldwind.util;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GridLayout;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;

import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.event.PositionEvent;
import gov.nasa.worldwind.event.PositionListener;
import gov.nasa.worldwind.event.RenderingEvent;
import gov.nasa.worldwind.event.RenderingListener;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwindx.examples.util.WWStyle;

/**
 * @author tag
 * @version $Id: StatusBar.java 1945 2014-04-18 17:08:43Z tgaskins $
 */
@SuppressWarnings("serial")
public class StatusBar extends JPanel implements PositionListener, RenderingListener
{
    // Units constants TODO: Replace with UnitsFormat
    public final static String UNIT_METRIC = "gov.nasa.worldwind.StatusBar.Metric";
    public final static String UNIT_IMPERIAL = "gov.nasa.worldwind.StatusBar.Imperial";

    protected static final int MAX_ALPHA = 254;

    private WorldWindow eventSource;
    private String elevationUnit = UNIT_METRIC;
    private String angleFormat = Angle.ANGLE_FORMAT_DD;

    protected final JLabel latDisplay = new JLabel("");
    protected final JLabel lonDisplay = new JLabel(Logging.getMessage("term.OffGlobe"));
    protected final JLabel altDisplay = new JLabel("");
    protected final JLabel eleDisplay = new JLabel("");
    protected final JLabel fpsDisplay = new JLabel("");

    protected AtomicBoolean showNetworkStatus = new AtomicBoolean(true);
    protected AtomicBoolean isNetworkAvailable = new AtomicBoolean(true);
    protected Thread netCheckThread;

    public StatusBar()
    {
        super(new BorderLayout());

        // Style the status bar panel itself.
        this.setBackground(WWStyle.BG_BASE);
        this.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, WWStyle.BORDER));
        this.setPreferredSize(new Dimension(0, 22));

        // seaglassfoundry.com: WGS84 label at left edge; FPS display added to stats row
        JLabel crsLabel = new JLabel(" WGS84 ");
        crsLabel.setFont(WWStyle.FONT_SMALL);
        crsLabel.setForeground(WWStyle.FG_DISABLED);
        crsLabel.setHorizontalAlignment(SwingConstants.CENTER);
        this.add(crsLabel, BorderLayout.WEST);

        final JLabel heartBeat = new JLabel(Logging.getMessage("term.Downloading"));

        // Apply WWStyle to all display labels.
        for (JLabel lbl : new JLabel[]{altDisplay, latDisplay, lonDisplay, eleDisplay, fpsDisplay, heartBeat}) {
            lbl.setFont(WWStyle.FONT_SMALL);
            lbl.setForeground(WWStyle.FG_SECONDARY);
            lbl.setHorizontalAlignment(SwingConstants.CENTER);
        }

        JPanel statsRow = new JPanel(new GridLayout(1, 0));
        statsRow.setBackground(WWStyle.BG_BASE);
        statsRow.add(altDisplay);
        statsRow.add(latDisplay);
        statsRow.add(lonDisplay);
        statsRow.add(eleDisplay);
        statsRow.add(fpsDisplay);
        statsRow.add(heartBeat);
        this.add(statsRow, BorderLayout.CENTER);

        // Heartbeat starts transparent; alpha is animated by the download timer below.
        heartBeat.setForeground(new Color(WWStyle.STATUS_ERROR.getRed(),
            WWStyle.STATUS_ERROR.getGreen(), WWStyle.STATUS_ERROR.getBlue(), 0));

        Timer downloadTimer = new Timer(100, actionEvent ->
        {
            if (!showNetworkStatus.get())
            {
                if (heartBeat.getText().length() > 0) {
					heartBeat.setText("");
				}
                return;
            }

            if (!isNetworkAvailable.get())
            {
                heartBeat.setText(Logging.getMessage("term.NoNetwork"));
                heartBeat.setForeground(new Color(WWStyle.STATUS_ERROR.getRed(),
                    WWStyle.STATUS_ERROR.getGreen(), WWStyle.STATUS_ERROR.getBlue(), MAX_ALPHA));
                return;
            }

            Color color = heartBeat.getForeground();
            int alpha = color.getAlpha();
            if (isNetworkAvailable.get() && WorldWind.getRetrievalService().hasActiveTasks())
            {
                heartBeat.setText(Logging.getMessage("term.Downloading"));
                if (alpha >= MAX_ALPHA) {
					alpha = MAX_ALPHA;
				} else {
					alpha = alpha < 16 ? 16 : Math.min(MAX_ALPHA, alpha + 20);
				}
            }
            else
            {
                alpha = Math.max(0, alpha - 20);
            }
            heartBeat.setForeground(new Color(WWStyle.STATUS_ERROR.getRed(),
                WWStyle.STATUS_ERROR.getGreen(), WWStyle.STATUS_ERROR.getBlue(), alpha));
        });
        downloadTimer.start();

        this.netCheckThread = this.startNetCheckThread();

        WorldWind.getNetworkStatus().addPropertyChangeListener(NetworkStatus.HOST_UNAVAILABLE,
            evt ->
            {
                Object nv = evt.getNewValue();
                String message = Logging.getMessage("NetworkStatus.UnavailableHost",
                    nv != null && nv instanceof URL ? ((URL) nv).getHost() : "Unknown");
                Logging.logger().info(message);
            });

        WorldWind.getNetworkStatus().addPropertyChangeListener(NetworkStatus.HOST_AVAILABLE,
            evt ->
            {
                Object nv = evt.getNewValue();
                String message = Logging.getMessage("NetworkStatus.HostNowAvailable",
                    nv != null && nv instanceof URL ? ((URL) nv).getHost() : "Unknown");
                Logging.logger().info(message);
            });
    }

    protected NetworkCheckThread startNetCheckThread()
    {
        NetworkCheckThread nct = new NetworkCheckThread(this.showNetworkStatus, this.isNetworkAvailable, null);
        nct.setDaemon(true);
        nct.start();

        return nct;
    }

    public void setEventSource(WorldWindow newEventSource)
    {
        if (this.eventSource != null)
        {
            this.eventSource.removePositionListener(this);
            this.eventSource.removeRenderingListener(this);
        }

        if (newEventSource != null)
        {
            newEventSource.addPositionListener(this);
            newEventSource.addRenderingListener(this);
        }

        this.eventSource = newEventSource;
    }

    public boolean isShowNetworkStatus()
    {
        return showNetworkStatus.get();
    }

    public void setShowNetworkStatus(boolean showNetworkStatus)
    {
        this.showNetworkStatus.set(showNetworkStatus);

        if (showNetworkStatus)
        {
            if (this.netCheckThread != null) {
				this.netCheckThread.interrupt();
			}

            this.netCheckThread = this.startNetCheckThread();
        }
        else
        {
            if (this.netCheckThread != null) {
				this.netCheckThread.interrupt();
			}

            this.netCheckThread = null;
        }
    }

    @Override
	public void moved(PositionEvent event)
    {
        this.handleCursorPositionChange(event);
    }

    public WorldWindow getEventSource()
    {
        return this.eventSource;
    }

    public String getElevationUnit()
    {
        return this.elevationUnit;
    }

    public void setElevationUnit(String unit)
    {
        if (unit == null)
        {
            String message = Logging.getMessage("nullValue.StringIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        this.elevationUnit = unit;
    }

    public String getAngleFormat()
    {
        return this.angleFormat;
    }

    public void setAngleFormat(String format)
    {
        if (format == null)
        {
            String message = Logging.getMessage("nullValue.StringIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        this.angleFormat = format;
    }

    protected String makeCursorElevationDescription(double metersElevation)
    {
        String s;
        String elev = Logging.getMessage("term.Elev");
        if (UNIT_IMPERIAL.equals(elevationUnit)) {
			s = String.format(elev + " %,7d feet", (int) (WWMath.convertMetersToFeet(metersElevation)));
		} else { // Default to metric units.
			s = String.format(elev + " %,7d meters", (int) metersElevation);
		}
        return s;
    }

    protected String makeEyeAltitudeDescription(double metersAltitude)
    {
        String s;
        String altitude = Logging.getMessage("term.Altitude");
        if (UNIT_IMPERIAL.equals(elevationUnit))
        {
            double miles = WWMath.convertMetersToMiles(metersAltitude);
            if (Math.abs(miles) >= 1) {
				s = String.format(altitude + " %,7d mi", (int) Math.round(miles));
			} else {
				s = String.format(altitude + " %,7d ft", (int) Math.round(WWMath.convertMetersToFeet(metersAltitude)));
			}
        }
        else if (Math.abs(metersAltitude) >= 1000) { // Default to metric units.
			s = String.format(altitude + " %,7d km", (int) Math.round(metersAltitude / 1e3));
		} else {
			s = String.format(altitude + " %,7d m", (int) Math.round(metersAltitude));
		}
        return s;
    }

    protected String makeAngleDescription(String label, Angle angle)
    {
        String s;
        if (Angle.ANGLE_FORMAT_DMS.equals(angleFormat)) {
			s = String.format("%s %s", label, angle.toDMSString());
		} else {
			s = String.format("%s %7.4f\u00B0", label, angle.degrees);
		}
        return s;
    }

    protected void handleCursorPositionChange(PositionEvent event)
    {
        Position newPos = event.getPosition();
        if (newPos != null)
        {
            String las = makeAngleDescription("Lat", newPos.getLatitude());
            String los = makeAngleDescription("Lon", newPos.getLongitude());
            String els = makeCursorElevationDescription(
                eventSource.getModel().getGlobe().getElevation(newPos.getLatitude(), newPos.getLongitude()));
            latDisplay.setText(las);
            lonDisplay.setText(los);
            eleDisplay.setText(els);
        }
        else
        {
            latDisplay.setText("");
            lonDisplay.setText(Logging.getMessage("term.OffGlobe"));
            eleDisplay.setText("");
        }
    }

    @Override
	public void stageChanged(RenderingEvent event)
    {
        if (!event.getStage().equals(RenderingEvent.BEFORE_BUFFER_SWAP)) {
			return;
		}

        EventQueue.invokeLater(() ->
        {
            if (eventSource.getView() != null && eventSource.getView().getEyePosition() != null) {
				altDisplay.setText(makeEyeAltitudeDescription(
                    eventSource.getView().getEyePosition().getElevation()));
			} else {
				altDisplay.setText(Logging.getMessage("term.Altitude"));
			}

            // seaglassfoundry.com: FPS from scene controller EMA (Phase 8)
            if (eventSource.getSceneController() != null) {
				fpsDisplay.setText(String.format("%.0f fps",
                    eventSource.getSceneController().getFramesPerSecond()));
			}
        });
    }
}
