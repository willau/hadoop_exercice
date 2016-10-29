/**
 * WARNING :
 * "SocialNetworkBFF" needs to exist before launching the main.
 * It also needs to have 2 families "friends" and "info".
 *
 * ConsoleReader is a class that handles all interactions with the user.
 * It will create a connection with the HBase Database and will use
 * a class UserHandler for each user to handle the creation of new user
 * or the update of existing user according to given to its questions.
 *
 * The social network enforces a few rules :
 * - For simplicity, each person has an unique name (non redundant)
 * - Bff value is mandatory (best friend for life)
 * - Every one has its own account (friends will be automatically created if they don't exist)
 * - An user's bff will have user as friend or as bff (reciprocity).
 * - A user can be its own bff.
 * - You are a bff or a friend not both (exclusivity).
 *
 * The console reader is case insensitive and only accepts as answers :
 * - Name without accents, special characters or numbers
 * - Age between 0 and 99
 *
 * Created by willyau on 26/10/16.
 */

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Table;

import java.io.IOException;
import java.util.Scanner;

import static java.util.regex.Pattern.matches;


public class ConsoleReader {
    Scanner scan ;

    // Constructor
    public ConsoleReader(){
        this.scan = new Scanner(System.in);
    }


    // Check format of answer with a given answer and a regex formula
    private boolean checkFormat(String answer, String responseType, String regexFormula){
        if( matches(regexFormula, answer.toLowerCase()) ){
            return true ;
        }else{
            System.out.println("Invalid format, please answer with a valid '" + responseType + "'");
            return false;
        }
    }


    // Confirmation of answer with only 'y' or 'n'
    private boolean askYN(String answer, String responseType){

        // If it's an empty string '', 'q' or 's', do not ask for confirmation !
        if( answer.equals("") || answer.equals("q") || answer.equals("s")) {
            return true;
        }

        // Ask for confirmation of answer
        System.out.println("Confirm that your " + responseType + " is '" + answer + "' (y/n)");
        String yesNo = "";
        boolean valid = false;

        // Until console receives 'y' or 'n' as answer, repeat question
        while ( !valid ){
            yesNo = this.scan.nextLine();
            valid = checkFormat(yesNo, "y or n", "^y$|^n$"); // Only accept 'y' or 'n'
        }

        // If answer is validated, return 'true' else, return 'false'
        if( yesNo.equals("y") ){
            return true;
        }
        else{
            return false;
        }
    }


    // Ask question to user with an expected response type and a regex formula to check format
    // Return an empty string "" if user skips question
    public String askQuestion(String question, String responseType, String regexFormula){
        String answer = "";
        boolean confirmation = false;

        // Until user provides valid answer AND confirms its answer with 'y' or 'n', repeat question
        while( !confirmation ){
            System.out.println(question);
            answer = this.scan.nextLine();

            // If format respects regex formula, ask for confirmation
            if( checkFormat(answer, responseType, regexFormula) ){
                confirmation = askYN(answer, responseType);
            }
        }
        return answer;
    }


    public static void main(String[] args) throws IOException {

        // Establishing connection to HBase
        Configuration conf = HBaseConfiguration.create();
        Connection connection = ConnectionFactory.createConnection(conf);

        try {
            // Access HBase table "SocialNetworkBFF" (it has to exist)
            Table table = connection.getTable(TableName.valueOf("SocialNetworkBFF"));
            System.out.println("\nConnection to HBase established\n\n\n");

            try {
                // Instance of ConsoleReader for asking input from user
                ConsoleReader consoleReader = new ConsoleReader();

                // Creating regex formula for checking answer format
                String qRegex       = "^$|^q$";                     // matches 'q' or ''
                String bffRegex     = "^[a-z]+$";                   // matches name with only alphabet standard character (no accents)
                String nameRegex    = "^$|".concat(bffRegex);       // matches name or ''
                String ageRegex     = "^$|^[1-9]$|^[1-9][1-9]$";    // matches 0 to 99 or ''
                String choiceRegex  = "^$|^flink$|^apex$|^spark$";  // matches 'apex','flink' or 'spark' or ''
                String sRegex       = "^$|^s$";                     // matches 's' or ''

                // Ask user if he wishes to enter SocialNetworkBFF
                boolean startSession = "".equals(consoleReader.askQuestion("Enter SocialNetworkBFF ? ('q' to quit / enter to continue)", "choice", qRegex));
                while( startSession ) {
                    // Asking for the name of the user
                    String name = consoleReader.askQuestion("What is your name ?", "name", nameRegex);
                    UserHandler user = new UserHandler(name, table);

                    // Asking information about the user
                    String bff      = consoleReader.askQuestion("Who is your best friend for life, a.k.a BFF ? (obligatory)", "bff name", bffRegex);
                    String friend   = consoleReader.askQuestion("Who is your other friend ? (enter to skip)", "friend", nameRegex);
                    String age      = consoleReader.askQuestion("How old are you ? (enter to skip)", "age", ageRegex);
                    String answer   = consoleReader.askQuestion("Do you like Flink, Apex or Spark ? (enter to skip)", "technology", choiceRegex);

                    // Adding main user's information (UserHandler instance user takes care of creating requests for updating database)
                    user = user.addBff(bff).addFriend(friend).addInfo("age", age).addInfo("technology", answer);

                    // Insert Update into HBase database
                    user.updateIntoDatabase();

                    // Ask if user wishes to quit SocialNetworkBFF
                    startSession = "s".equals(consoleReader.askQuestion("Quit SocialNetworkBFF ? (enter to quit / 's' to stay )", "choice", sRegex));
                }

            }finally {
                // Close table
                if( table != null ) table.close();
            }

        }finally{
            // Close connection
            connection.close();
        }

    }
}
