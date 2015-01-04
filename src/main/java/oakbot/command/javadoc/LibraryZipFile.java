package oakbot.command.javadoc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import oakbot.util.DocumentWrapper;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Represents a ZIP file that was generated by OakbotDoclet, which contains
 * Javadoc information.
 * @author Michael Angstadt
 */
public class LibraryZipFile {
	private static final String extension = ".xml";
	private static final String infoFileName = "info" + extension;

	private final Path file;
	private final String baseUrl, name, version, projectUrl;

	public LibraryZipFile(Path file) throws IOException {
		this.file = file.toRealPath();

		try (FileSystem fs = FileSystems.newFileSystem(file, null)) {
			Path info = fs.getPath("/" + infoFileName);
			if (!Files.exists(info)) {
				baseUrl = name = version = projectUrl = null;
				return;
			}

			Element infoElement;
			try (InputStream in = Files.newInputStream(info)) {
				DocumentWrapper document = new DocumentWrapper(DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in));
				infoElement = document.element("/info");
			} catch (ParserConfigurationException e) {
				//should never be thrown
				throw new RuntimeException(e);
			} catch (SAXException e) {
				//XML parse error
				throw new IOException(e);
			}

			if (infoElement == null) {
				baseUrl = name = version = projectUrl = null;
				return;
			}

			String name = infoElement.getAttribute("name");
			this.name = name.isEmpty() ? null : name;

			String baseUrl = infoElement.getAttribute("baseUrl");
			if (baseUrl.isEmpty()) {
				this.baseUrl = null;
			} else {
				this.baseUrl = baseUrl + (baseUrl.endsWith("/") ? "" : "/");
			}

			String projectUrl = infoElement.getAttribute("projectUrl");
			this.projectUrl = projectUrl.isEmpty() ? null : projectUrl;

			String version = infoElement.getAttribute("version");
			this.version = version.isEmpty() ? null : version;
		}
	}

	/**
	 * Gets the URL to a class's Javadoc page (with frames).
	 * @param info the class
	 * @return the URL or null if no base URL was given
	 */
	public String getFrameUrl(ClassInfo info) {
		if (baseUrl == null) {
			return null;
		}
		return baseUrl + "index.html?" + info.getName().getFull().replace('.', '/') + ".html";
	}

	/**
	 * Gets the URL to a class's Javadoc page (without frames).
	 * @param info the class
	 * @return the URL or null if no base URL was given
	 */
	public String getUrl(ClassInfo info) {
		if (baseUrl == null) {
			return null;
		}
		return baseUrl + info.getName().getFull().replace('.', '/') + ".html";
	}

	/**
	 * Gets a list of all classes that are in the library.
	 * @return the fully-qualified names of all the classes
	 * @throws IOException if there's a problem reading the ZIP file
	 */
	public Iterator<ClassName> getClasses() throws IOException {
		final FileSystem fs = FileSystems.newFileSystem(file, null);
		final DirectoryStream<Path> stream = Files.newDirectoryStream(fs.getPath("/"), entry -> {
			String name = entry.getFileName().toString();
			if (!name.endsWith(extension)) {
				return false;
			}

			return !name.equals(infoFileName);
		});

		final Iterator<Path> it = stream.iterator();
		return new Iterator<ClassName>() {
			@Override
			public boolean hasNext() {
				boolean hasNext = it.hasNext();
				if (!hasNext) {
					try {
						stream.close();
					} catch (IOException e) {
						//ignore
					}
					try {
						fs.close();
					} catch (IOException e) {
						//ignore
					}
				}

				return hasNext;
			}

			@Override
			public ClassName next() {
				Path file = it.next();
				String fileName = file.getFileName().toString();
				String fullName = fileName.substring(0, fileName.length() - extension.length());
				return new ClassName(fullName);
			}
		};
	}

	/**
	 * Gets the parsed XML DOM of the given class.
	 * @param fullName the fully-qualifed class name (e.g. "java.lang.String")
	 * @return the XML DOM or null if the class was not found
	 * @throws IOException if there was a problem reading from the ZIP file or
	 * parsing the XML
	 */
	public ClassInfo getClassInfo(String fullName) throws IOException {
		try (FileSystem fs = FileSystems.newFileSystem(file, null)) {
			Path path = fs.getPath(fullName + extension);
			if (!Files.exists(path)) {
				return null;
			}

			Document document;
			try (InputStream in = Files.newInputStream(path)) {
				document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
			} catch (SAXException | ParserConfigurationException e) {
				throw new IOException(e);
			}

			return new ClassInfoXmlParser(document, this).parse();
		}
	}

	/**
	 * Gets the base URL of this library's Javadocs.
	 * @return the base URL or null if none was defined
	 */
	public String getBaseUrl() {
		return baseUrl;
	}

	/**
	 * Gets the name of this library.
	 * @return the name (e.g. "jsoup") or null if none was defined
	 */
	public String getName() {
		return name;
	}

	/**
	 * Gets the version number of this library.
	 * @return the version number (e.g. "1.8.1") or null if none was defined
	 */
	public String getVersion() {
		return version;
	}

	/**
	 * Gets the URL to the library's webpage.
	 * @return the URL or null if none was defined
	 */
	public String getProjectUrl() {
		return projectUrl;
	}

	public Path getPath() {
		return file;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((file == null) ? 0 : file.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		LibraryZipFile other = (LibraryZipFile) obj;
		if (file == null) {
			if (other.file != null) return false;
		} else if (!file.equals(other.file)) return false;
		return true;
	}
}