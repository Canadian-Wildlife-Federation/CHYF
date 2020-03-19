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

package net.refractions.chyf.watershed.inputprep;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import net.refractions.chyf.watershed.model.HydroEdge;


/**
 * Provides a way to filter out topologically duplicate {@link HydroEdge} geometries before
 * processing them.
 * 
 * @author Gareth Gilson
 * @version 1.1
 */
public class DuplicateHydroEdgeRemover {

    private Set<HydroEdge> filteredHydroEdges;

    /**
     * Construct a new DuplicateHydroEdgeRemover and move the given {@link HydroEdge} through the
     * filter.
     * 
     * @param allHydroEdges The HydroEdges to move through the filter.
     */
    public DuplicateHydroEdgeRemover(Collection<HydroEdge> allHydroEdges) {

        filteredHydroEdges = new TreeSet<HydroEdge>(allHydroEdges);
    }

    /**
     * Add more {@link HydroEdge HydroEdges} through the filter.
     * 
     * @param hydroEdges The new HydroEdges to move through the filter.
     */
    public void addHydroEdges(Collection<HydroEdge> hydroEdges) {

        filteredHydroEdges.addAll(hydroEdges);
    }

    /**
     * Get the remaining {@link HydroEdge HydroEdges}, that is, the ones left after removing the
     * topographically equal HydroEdges.
     * 
     * @return A {@link Collection} of dissimilar HydroEdges.
     */
    public Collection<HydroEdge> getRemainingHydroEdges() {

        return filteredHydroEdges;
    }
}
