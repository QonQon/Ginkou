package se.ginkou.banking;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.webharvest.definition.ScraperConfiguration;
import org.webharvest.runtime.Scraper;
import org.webharvest.runtime.ScraperContext;
import org.webharvest.runtime.variables.Variable;

import se.ginkou.Account;
import se.ginkou.Transaction;
public class XmlParser {
	private String xml;
	private String userid;
	private String password;
	
	/**
	 * Create a new XmlParser with the given xml file, userid and password.
	 * @param xmlFile the file to use as a parser.
	 * @param userid the userid to use in the parser.
	 * @param password the password to use in the parser.
	 */
	public XmlParser(String xmlFile, String userid, String password) {
		this.xml = xmlFile;
		this.userid = userid;
		this.password = password;
	}
	
	/**
	 * Create a new XmlParser with the given xml file.
	 * @param xmlFile the file to use as a parser.
	 */
	public XmlParser(String xmlFile) {
		this(xmlFile, null, null);
	}
	
	public List<Transaction> run(){
		// register external plugins if there are any
		ArrayList<Transaction> transactions = new ArrayList<Transaction>();

		ScraperConfiguration config;
		try {
			config = new ScraperConfiguration(xml);
			Scraper scraper = new Scraper(config, ".");
			
			//scraper.getHttpClientManager().setHttpProxy("localhost", 8888); //Fiddling ;)
			if (userid != null) {
				scraper.addVariableToContext("userid", userid);
			}
			if (password != null) {
				scraper.addVariableToContext("passwd", password);
			}
			
			//scraper.setDebug(true);
			scraper.execute();	
			
			// takes variable created during execution			
			ScraperContext context = scraper.getContext();
			//System.out.println(context.size());
			//System.out.println(context);			
			
			Variable account, date, notice, amount;
			int q = 1;
			while((account = (Variable) context.get("account."+q))  != null){

				int i=1;  
				while ((date = (Variable) context.get("date."+q+"."+i))  != null && 
						(notice = (Variable) context.get("notice."+q+"."+i))  != null &&
						(amount = (Variable) context.get("amount."+q+"."+i))  != null){
					transactions.add(new Transaction(
							new Account(Long.parseLong(account.toString().replaceAll("[\\-\\s]", "")), ""), 
							DateTime.parse(date.toString().trim(), DateTimeFormat.forPattern("yyMMdd")), 
							notice.toString(), 
							Double.parseDouble(amount.toString().replace(".", "").replace(",", "."))));
					i++;  
				}
				++q;
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return transactions;
	}

	public static void main(String[] args) {
		XmlParser parser = new XmlParser("SEB.xml", "8702190011", "ingetINGET5");
		List<Transaction> trans = parser.run();
		for(Transaction t: trans)
			System.out.println(t);
		System.out.println(trans.size());
	}
}