package nu.validator.client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import nu.validator.htmlparser.sax.XmlSerializer;
import nu.validator.localentities.LocalCacheEntityResolver;
import nu.validator.messages.MessageEmitterAdapter;
import nu.validator.messages.XmlMessageEmitter;
import nu.validator.servlet.imagereview.ImageCollector;
import nu.validator.source.SourceCode;
import nu.validator.spec.Spec;
import nu.validator.spec.html5.Html5SpecBuilder;
import nu.validator.validation.SimpleDocumentValidator;
import nu.validator.validation.SimpleDocumentValidator.SchemaReadException;
import nu.validator.xml.SystemErrErrorHandler;

import org.apache.commons.io.output.TeeOutputStream;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.TemplateResolver;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * An utility for Nu validator that reads <code>sitemap.xml</code> and validates
 * all URL found in that file. For each URL the validation result is stored in a
 * file. The <code>index.html</code> file contains links to all validation
 * results.
 * 
 * @author Mariusz Jakubowski
 *
 */
public class SitemapValidator {

	private static MessageEmitterAdapter errorHandler;
	private static SimpleDocumentValidator validator;
	private static List<ValidationResult> results = new ArrayList<>();

	/**
	 * Info about validation result.
	 *
	 */
	@SuppressWarnings("unused")
	private static class ValidationResult implements
			Comparable<ValidationResult> {
		public final String url;
		public final String fileName;
		public final int errors;
		public final int warnings;
		public final long time;

		public ValidationResult(String url, String fileName, int errors,
				int warnings, long time) {
			this.url = url;
			this.fileName = fileName;
			this.errors = errors;
			this.warnings = warnings;
			this.time = time;
		}

		@Override
		public int compareTo(ValidationResult o) {
			int idx = (o.errors - errors) * 10000 + o.warnings - warnings;
			return idx;
		}
	}

	/**
	 * Parses the sitemap.xml file.
	 *
	 */
	private static class SitemapHandler extends DefaultHandler {
		private StringBuilder content = new StringBuilder();

		@Override
		public void startElement(String uri, String localName, String qName,
				Attributes attributes) throws SAXException {
			if ("loc".equals(qName)) {
				content.setLength(0);
			}
		}

		@Override
		public void endElement(String uri, String localName, String qName)
				throws SAXException {
			if ("loc".equals(qName)) {
				validate(content.toString());
			}
		}

		@Override
		public void characters(char[] ch, int start, int length)
				throws SAXException {
			content.append(String.copyValueOf(ch, start, length).trim());
		}

		@Override
		public void endDocument() throws SAXException {
			buildIndex();
		}

	}

	/**
	 * Builds index.html file. This file contains table with links to validation
	 * results.
	 */
	private static void buildIndex() {
		try {
			Collections.sort(results);

			FileWriter writer = new FileWriter(new File("out", "index.html"));

			TemplateEngine templateEngine = new TemplateEngine();
			TemplateResolver templateResolver = new ClassLoaderTemplateResolver();
			templateResolver.setPrefix("resources/");
			templateResolver.setSuffix(".html");
			templateEngine.setTemplateResolver(templateResolver);

			Context ctx = new Context();
			ctx.setVariable("results", results);

			templateEngine.process("index-template", ctx, writer);

			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Validates a document at given URL. The result of validation is stored in
	 * xml and html file.
	 * 
	 * @param url
	 *            an URL to a document to validate
	 */
	private static void validate(String url) {
		System.out.println(url);
		try {
			String fileName = getFileName(url);

			PipedOutputStream out = new PipedOutputStream();

			OutputStream outFile = new FileOutputStream(new File("out",
					fileName + ".xml"));

			TeeOutputStream out2 = new TeeOutputStream(out, outFile);

			PipedInputStream pis = new PipedInputStream(out, 64 * 1024);

			setErrorHandler(out2);
			validator.setUpValidatorAndParsers(errorHandler, false, false);
			Runnable task = () -> {
				toHTML(pis, fileName + ".html");
			};
			new Thread(task).start();

			long time = System.currentTimeMillis();
			validator.checkHttpURL(new URL(url));
			time = System.currentTimeMillis() - time;
			errorHandler.end("ok", "fail");
			out2.close();

			int errors = errorHandler.getErrors()
					+ errorHandler.getFatalErrors();
			int warnings = errorHandler.getWarnings();
			ValidationResult result = new ValidationResult(url, fileName
					+ ".html", errors, warnings, time);
			results.add(result);
		} catch (IOException | SAXException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Transforms an xml with validation result to an html using html.xslt.
	 * 
	 * @param pis
	 *            xml with validation result
	 * @param fileName
	 *            a name of a file to write output to
	 * @throws TransformerFactoryConfigurationError
	 */
	private static void toHTML(PipedInputStream pis, String fileName)
			throws TransformerFactoryConfigurationError {
		InputStream xslt = SitemapValidator.class
				.getResourceAsStream("/resources/html.xslt");
		StreamSource stylesource = new StreamSource(xslt);
		TransformerFactory tFactory = TransformerFactory.newInstance();
		try {
			Transformer transformer = tFactory.newTransformer(stylesource);
			StreamResult result = new StreamResult(new FileOutputStream(
					new File("out", fileName)));
			Source source = new StreamSource(pis);
			transformer.transform(source, result);
			result.getOutputStream().close();
		} catch (TransformerConfigurationException e) {
			e.printStackTrace();
		} catch (TransformerException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Generates a file name from an url.
	 * 
	 * @param url
	 *            a url with a document to validate
	 * @return a name of a file where validation result will be written
	 */
	private static String getFileName(String url) {
		try {
			URL url2 = new URL(url);
			String path = url2.getPath();
			int i = path.indexOf(".nsf");
			if (i > -1) {
				path = path.substring(i + 4);
			}
			if (path.length() > 0 && path.charAt(0) == '/') {
				path = path.substring(1);
			}
			String name = path;
			if (url2.getQuery() != null) {
				name += '_' + url2.getQuery();
			}
			name = name.replace('/', '_').replace('?', '_').replace('&', '_');
			System.out.println("writing " + name);
			return name;
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		return url;
	}

	/**
	 * Sets up a validator.
	 * 
	 * @param schemaUrl
	 * @throws SAXException
	 * @throws Exception
	 */
	private static void setup(String schemaUrl) throws SAXException, Exception {
		try {
			validator.setUpMainSchema(schemaUrl, new SystemErrErrorHandler());
		} catch (SchemaReadException e) {
			System.out.println(e.getMessage() + " Terminating.");
			System.exit(1);
		} catch (StackOverflowError e) {
			System.out.println("StackOverflowError"
					+ " while evaluating HTML schema.");
			System.out.println("The checker requires a java thread stack size"
					+ " of at least 512k.");
			System.out.println("Consider invoking java with the -Xss"
					+ " option. For example:");
			System.out.println("\n  java -Xss512k -jar ~/vnu.jar FILE.html");
			System.exit(1);
		}
	}

	/**
	 * Setup up an error handler for validator.
	 * 
	 * @param out
	 * @throws SAXException
	 */
	private static void setErrorHandler(OutputStream out) throws SAXException {
		SourceCode sourceCode = validator.getSourceCode();
		ImageCollector imageCollector = new ImageCollector(sourceCode);
		boolean showSource = false;
		XmlMessageEmitter messageEmitter = new XmlMessageEmitter(
				new XmlSerializer(out));
		errorHandler = new MessageEmitterAdapter(sourceCode, showSource,
				imageCollector, 0, true, messageEmitter);
		errorHandler.setErrorsOnly(false);
		errorHandler.setHtml(true);
		errorHandler.start(null);
		try {
			Spec html5spec = Html5SpecBuilder
					.parseSpec(LocalCacheEntityResolver.getHtml5SpecAsStream());
			errorHandler.setSpec(html5spec);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws SAXException, Exception {
		if (args.length == 0) {
			System.out.println("Usage:");
			System.out.println("SitemapValidator http://url/to/sitemap.xml");
			System.out.println("or");
			System.out.println("SitemapValidator path/to/sitemap.xml");
		} else {
			new File("out").mkdir();

			validator = new SimpleDocumentValidator();
			String schemaUrl = "http://s.validator.nu/html5-rdfalite.rnc";
			setup(schemaUrl);

			SAXParserFactory parserFactor = SAXParserFactory.newInstance();
			SAXParser parser = parserFactor.newSAXParser();
			SitemapHandler handler = new SitemapHandler();
			if (args[0].startsWith("http")) {
				parser.parse(args[0], handler);
			} else {
				parser.parse(new File(args[0]), handler);
			}
		}
	}

}
