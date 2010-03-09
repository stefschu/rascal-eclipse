package org.rascalmpl.eclipse.console;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;

import org.rascalmpl.uri.BadURIException;
import org.rascalmpl.uri.IURIInputStreamResolver;
import org.rascalmpl.uri.IURIOutputStreamResolver;

public class ConsoleURIResolver implements IURIInputStreamResolver, IURIOutputStreamResolver{
	
	public static URI constructConsoleURI(String id){
		try{
			return new URI("console://"+id);
		}catch(URISyntaxException usex){
			throw new BadURIException(usex);
		}
	}
	
	public ConsoleURIResolver(){
		super();
	}
	
	public String scheme(){
		return "console";
	}
	
	public InputStream getInputStream(URI uri) throws IOException{
		throw new UnsupportedOperationException("Not supported by console.");
	}

	public OutputStream getOutputStream(URI uri, boolean append) throws IOException{
		throw new UnsupportedOperationException("Not supported by console.");
	}

}