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

package net.refractions.chyf.util;

public class KDNode<T> {
	
	T item = null;
	Axis splitAxis;
	KDNode<T> left = null;
	KDNode<T> right = null;
	
	@Override
	public String toString() {
		return toString("");
	}
	
	private String toString(String indent) {
		return indent + item + " " + splitAxis
				+ (left != null || right != null ?
						"\n" + (left == null ? indent + "  null" : left.toString(indent + "  "))
								+ "\n" + (right == null ? indent + "  null"
										: right.toString(indent + "  ")) : "");
	}
	
		
}
