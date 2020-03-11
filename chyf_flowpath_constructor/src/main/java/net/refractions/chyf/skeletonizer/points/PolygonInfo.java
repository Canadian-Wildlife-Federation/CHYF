/*
 * Copyright 2019 Government of Canada
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */
package net.refractions.chyf.skeletonizer.points;

import org.locationtech.jts.geom.Polygon;
import org.opengis.filter.identity.FeatureId;

/**
 * Class for tracking information about a polygon
 * @author Emily
 *
 */
public class PolygonInfo {

	
	private FeatureId featureId;
	private boolean isModified;
	
	private String catchmentInternalID;
	 
	public PolygonInfo(FeatureId fid, String catchmentInternalId) {
		this.featureId = fid;
		this.catchmentInternalID = catchmentInternalId;
	}
	
	public String getCatchmentId(){
		return this.catchmentInternalID;
	}
	public FeatureId getFeatureId() {
		return this.featureId;
	}
	
	public boolean isModified() {
		return this.isModified;
	}
	
	public void setModified(boolean modified) {
		this.isModified = modified;
	}
	
	public static void setModified(Polygon p, boolean modified) {
		if (p.getUserData() == null) p.setUserData(new PolygonInfo(null, null));
		((PolygonInfo)p.getUserData()).setModified(modified);
	}
	
	public static boolean isModified(Polygon p) {
		return ((PolygonInfo)p.getUserData()).isModified();
	}
	
	public static FeatureId getFeatureId(Polygon p) {
		return ((PolygonInfo)p.getUserData()).getFeatureId();
	}
	
	public static String getCatchmentId(Polygon p) {
		return ((PolygonInfo)p.getUserData()).getCatchmentId();
	}
}
