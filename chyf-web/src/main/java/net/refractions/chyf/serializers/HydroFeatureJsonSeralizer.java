/*
 * Copyright 2022 Canadian Wildlife Federation
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
package net.refractions.chyf.serializers;

import java.io.IOException;

import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.stereotype.Component;

import net.refractions.chyf.model.ECatchment;
import net.refractions.chyf.model.HydroFeature;

/**
 * Serializes a single hydro feature to GeoJSON
 * 
 * @author Emily
 *
 */
@Component
public class HydroFeatureJsonSeralizer extends AbstractHttpMessageConverter<HydroFeature>{


	public HydroFeatureJsonSeralizer() {
		super(MediaType.APPLICATION_JSON);
	}

	@Override
	protected boolean supports(Class<?> clazz) {
		return HydroFeature.class.isAssignableFrom(clazz);
	}

	@Override
	protected ECatchment readInternal(Class<? extends HydroFeature> clazz, HttpInputMessage inputMessage)
			throws IOException, HttpMessageNotReadableException {
		return null;
	}

	@Override
	protected void writeInternal(HydroFeature feature, HttpOutputMessage outputMessage)
			throws IOException, HttpMessageNotWritableException {
	
		GeoJsonUtils.INSTANCE.writeFeature(feature, outputMessage.getBody());
	}

}
