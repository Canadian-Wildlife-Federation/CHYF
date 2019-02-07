package net.refractions.chyf.rest.messageconverters;

import java.io.IOException;
import java.nio.charset.Charset;

import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

public class GeoPackageConverter extends AbstractHttpMessageConverter<ApiResponse> {

	private static MediaType GEOPACKAGE = new MediaType("application", "geopackage+sqlite3", Charset.forName("UTF-8"));
	
	public GeoPackageConverter() {
		super(GEOPACKAGE);
	}

	@Override
	protected boolean supports(Class<?> clazz) {
		return ApiResponse.class.isAssignableFrom(clazz);
	}

	@Override
	protected ApiResponse readInternal(
			Class<? extends ApiResponse> clazz,
			HttpInputMessage inputMessage) throws IOException,
			HttpMessageNotReadableException {
		return null;
	}

	@Override
	protected void writeInternal(ApiResponse response,
			HttpOutputMessage outputMessage) throws IOException,
			HttpMessageNotWritableException {
		GeoPackageHelper helper = new GeoPackageHelper(outputMessage.getBody());
		helper.convertResponse(response);
		outputMessage.getBody().flush();
	}

}
