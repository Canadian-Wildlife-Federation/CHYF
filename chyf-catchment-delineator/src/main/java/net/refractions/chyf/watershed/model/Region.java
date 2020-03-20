/*******************************************************************************
 * Copyright 2020 Government of Canada
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
 *******************************************************************************/

package net.refractions.chyf.watershed.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.locationtech.jts.geom.Triangle;



/**
 * Models a connected region of {@link Triangle}s.
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class Region {
    /**
     * The ID for an unassigned region
     */
    public static final int NULL_ID       = -1;

    /**
     * The ID given to the "region" which lies outside the core TIN area-of-interest
     */
    public static final int BORDER_ID     = -2;

    /**
     * The types of regions
     */
    public final static int TYPE_DRAINAGE = 1;
    public final static int TYPE_PIT      = 2;

    private List<WatershedTriangle> members = new ArrayList<WatershedTriangle>();
    private int id;
    private int type = TYPE_DRAINAGE;

    Region(int id) {
        this.id = id;
    }

    public int getID() {
        return id;
    }

    public void setType(int type) {
        this.type = type;
    }
    public int getType() {
        return type;
    }

    public boolean isBorder() {
        return id == BORDER_ID;
    }

    /**
     * Gets the triangles in this region.
     * 
     * @return List<WatershedTriangle>
     */
    public List<WatershedTriangle> getMembers() {
        return members;
    }

    public void add(WatershedTriangle tri) {
        // remove from old region, if any
        Region currRegion = tri.getRegion();
        if (currRegion == this)
            return;
        if (currRegion != null)
            currRegion.remove(tri);

        // Assert.isTrue(tri.getRegion() == null, "Region assignment to triangle woth non-null
        // region");
        members.add(tri);
        tri.setRegion(this);
    }

    public void remove(WatershedTriangle tri) {
        members.remove(tri);
        tri.setRegion(null);
    }

    public boolean isDrained() {
        return id > 0;
    }

    /**
     * Merges one region into another. Always merges into a drained region, if possible
     * 
     * @param region
     */
    public void merge(Region region) {
        Region target = this;
        Region mergee = region;
        /*
         * MD - obsolete? // always merge into a drained region if (! target.isDrained()) { target =
         * region; mergee = this; }
         */

        if (mergee.members == null)
            System.out.println("found null (region " + mergee.getID());

        if (mergee.members != null) {
            setRegion(mergee.members, target);
            target.members.addAll(mergee.members);
            // invalidate merged region
            mergee.members = null;
        }
    }

    /**
     * Sets the Region for all {@link WatershedTriangle}s in a Collection.
     * 
     * @param tris
     * @param region
     */
    public static void setRegion(Collection<WatershedTriangle> tris, Region region) {
        for (WatershedTriangle tri : tris) {
            tri.setRegion(region);
        }
    }

    public String toString() {
        return Integer.toString(id);
    }
}