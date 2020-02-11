package com.alphawallet.token.web;

import com.alphawallet.token.entity.AttributeInterface;
import com.alphawallet.token.entity.AttributeType;
import com.alphawallet.token.entity.ContractAddress;
import com.alphawallet.token.entity.ContractInfo;
import com.alphawallet.token.entity.TokenScriptResult;
import com.alphawallet.token.entity.TransactionResult;
import com.alphawallet.token.tools.TokenDefinition;
import com.alphawallet.token.web.Ethereum.TokenscriptFunction;
import com.alphawallet.token.web.Ethereum.TransactionHandler;
import com.alphawallet.token.web.Service.CryptoFunctions;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.xml.sax.SAXException;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.reactivex.Single;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import static com.alphawallet.token.tools.TokenDefinition.TOKENSCRIPT_CURRENT_SCHEMA;
import static com.alphawallet.token.tools.TokenDefinition.TOKENSCRIPT_REPO_SERVER;
import static com.alphawallet.token.web.Ethereum.TokenscriptFunction.ZERO_ADDRESS;

@Controller
@SpringBootApplication
@RequestMapping("/")
public class AppSiteController implements AttributeInterface
{
    private static CryptoFunctions cryptoFunctions = new CryptoFunctions();
    private static Map<Integer, Map<String, File>> addresses;
    private static Map<Integer, Map<String, Map<BigInteger, CachedResult>>> transactionResults = new ConcurrentHashMap<>();  //optimisation results
    private final TokenscriptFunction tokenscriptFunction = new TokenscriptFunction() { };
    private static Path repoDir;
    private static String infuraKey = "da3717f25f824cc1baa32d812386d93f";
    private static OkHttpClient client;

    @GetMapping("/")
    public @ResponseBody String home(@RequestParam(value = "token", required = false) String tokenContract,
                                     @RequestParam(value = "chainId", required = false) String chainIdStr,
                                     @RequestParam(value = "tokenId", required = false) String tokenId) throws IOException, SAXException, NoHandlerFoundException
    {
        int chainId = 1;
        if (tokenContract == null || tokenContract.length() == 0) tokenContract = "0x89d142bef8605646881c68dcd48cdaf17fe597dc";
        if (chainIdStr != null && chainIdStr.length() > 0)
        {
            try
            {
                chainId = Integer.parseInt(chainIdStr);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        if (tokenId == null || tokenId.length() == 0) tokenId = "32303234303730363231303030302b30333030010200415500464a0101000400";

        return handleToken(chainId, tokenId, tokenContract);
    }

    private String handleToken(int chainId, String tokenId, String contract
                                  ) throws IOException, SAXException, NoHandlerFoundException
    {
        TokenDefinition definition = getTokenDefinition(chainId, contract);

        //get attributes
        BigInteger firstTokenId = new BigInteger(tokenId, 16);

        System.out.println(firstTokenId.toString(16));
        ContractAddress cAddr = new ContractAddress(chainId, contract);
        StringBuilder tokenData = new StringBuilder();
        TransactionHandler txHandler = new TransactionHandler(chainId);

        String tokenName = txHandler.getNameOnly(contract);
        String symbol = txHandler.getSymbolOnly(contract);

        try
        {
            TokenScriptResult.addPair(tokenData, "name", tokenName);
            TokenScriptResult.addPair(tokenData, "symbol", symbol);
            TokenScriptResult.addPair(tokenData, "_count", String.valueOf(1));
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        tokenscriptFunction.resolveAttributes(ZERO_ADDRESS, firstTokenId, this, cAddr, definition)
                .forEach(attr -> TokenScriptResult.addPair(tokenData, attr.id, attr.text))
                .isDisposed();

        String view = definition.getCardData("view");
        String style = definition.getCardData("style");

        String scriptData = loadFile("templates/token_inject.js.tokenscript");
        String tokenView = String.format(scriptData, tokenData.toString(), view);

        return formWebPage(contract, style, tokenView, tokenName, symbol);
    }

    private String formWebPage(
            String contractAddress,
            String style,
            String tokenView,
            String tokenName,
            String symbol
    )
    {
        String nameWithSymbol = tokenName + "(" + symbol + ")";

        String initHTML = loadFile("templates/tokenscriptTemplate.html");

        return String.format(
                initHTML,
                nameWithSymbol,
                style,
                tokenView,
                contractAddress
        );
    }

    private TokenDefinition getTokenDefinition(int chainId, String contractAddress) throws IOException, SAXException, NoHandlerFoundException
    {
        File xml = getTokenScriptFile(chainId, contractAddress);
        TokenDefinition definition = readTokenScriptFile(xml);
        if (xml == null)
        {
            File fetchedFile = fetchTokenScriptFromServer(chainId, contractAddress).blockingGet();
            if (fetchedFile != null && fetchedFile.exists())
            {
                definition = readTokenScriptFile(fetchedFile);
            }
        }

        return definition;
    }

    private TokenDefinition readTokenScriptFile(File script) throws IOException, SAXException
    {
        TokenDefinition definition = null;
        if (script != null && script.exists())
        {
            try(FileInputStream in = new FileInputStream(script)) {
                definition = new TokenDefinition(in, new Locale("en"), null);
            }
        }

        return definition;
    }

    private File getTokenScriptFile(int chainId, String contractAddress)
    {
        if (addresses.containsKey(chainId) && addresses.get(chainId).containsKey(contractAddress))
        {
            return addresses.get(chainId).get(contractAddress);
        }
        else
        {
            return null;
        }
    }

    @Value("${repository.dir}")
    public void setRepoDir(String value) {
        repoDir = Paths.get(value);
        File check = repoDir.toFile();
        if (!check.exists())
        {
            check.mkdir();
        }
    }

    public static void main(String[] args) throws IOException { // TODO: should run System.exit() if IOException
        addresses = new HashMap<Integer, Map<String, File>>();
        SpringApplication.run(AppSiteController.class, args);
        try (Stream<Path> dirStream = Files.walk(repoDir)) {
            dirStream.filter(path -> path.toString().toLowerCase().endsWith(".tsml"))
                    .filter(Files::isRegularFile)
                    .filter(Files::isReadable)
                    .forEach(AppSiteController::addContractAddresses);

            assert addresses != null : "Can't read all XML files";
        } catch (NoSuchFileException e) {
            System.err.println("repository.dir property is defined with a non-existing dir: " + repoDir.toString());
            System.err.println("Please edit your local copy of application.properties, or");
            System.err.println("try run with --repository.dir=/dir/to/repo");
            System.exit(255);
        } catch (AssertionError e) {
            System.err.println("Can't read all the XML files in repository.dir: " + repoDir.toString());
            System.exit(254);
        }

        if (addresses.size() > 0) {
            // the list should be reprinted whenever a new file is added.
            System.out.println("Serving an XML repo with the following contracts:");
            addresses.forEach((chainId, addrMap) -> {
                System.out.println("Network ID: " + chainId);
                addrMap.forEach((addr, xml) -> {
                    System.out.println(addr + ":" + xml.getPath());
                });
                System.out.println(" ------------");
            });
        }

        loadInfuraKey();

        client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build();
	}

    private static void addContractAddresses(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            TokenDefinition token = new TokenDefinition(input, new Locale("en"), null);
            ContractInfo holdingContracts = token.contracts.get(token.holdingToken);
            if (holdingContracts != null)
                holdingContracts.addresses.keySet().stream().forEach(network -> addContractsToNetwork(network, networkAddresses(holdingContracts.addresses.get(network), path.toString())));
        } catch (IOException | SAXException e) {
            throw new RuntimeException(e); // make it safe to use in stream
        }
    }

    private static void addContractsToNetwork(Integer network, Map<String, File> newTokenDescriptionAddresses)
    {
        Map<String, File> existingDefinitions = addresses.get(network);
        if (existingDefinitions == null) existingDefinitions = new HashMap<>();

        addresses.put(network, Stream.concat(existingDefinitions.entrySet().stream(), newTokenDescriptionAddresses.entrySet().stream())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (value1, value2) -> new File(value2.getAbsolutePath())
                         )
                ));
    }

    private static Map<String, File> networkAddresses(List<String> strings, String path)
    {
        Map<String, File> addrMap = new HashMap<>();
        strings.forEach(address -> addrMap.put(address, new File(path)));
        return addrMap;
    }

    private String loadFile(String fileName) {
        byte[] buffer = new byte[0];
        try {
            InputStream in = getClass()
                    .getClassLoader().getResourceAsStream(fileName);
            buffer = new byte[in.available()];
            int len = in.read(buffer);
            if (len < 1) {
                throw new IOException("Nothing is read.");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return new String(buffer);
    }

    //These functions are for caching and restoring results for optimsation.
    @Override
    public TransactionResult getFunctionResult(ContractAddress contract, AttributeType attr, BigInteger tokenId)
    {
        String addressFunctionKey = contract.address + "-" + attr.id;
        TransactionResult tr = new TransactionResult(contract.chainId, contract.address, tokenId, attr);
        //existing entry in map?
        if (transactionResults.containsKey(contract.chainId))
        {
            Map<BigInteger, CachedResult> contractResult = transactionResults.get(contract.chainId).get(addressFunctionKey);
            if (contractResult != null && contractResult.containsKey(tokenId))
            {
                tr.resultTime = contractResult.get(tokenId).resultTime;
                tr.result = contractResult.get(tokenId).result;
            }
        }

        return tr;
    }

    @Override
    public TransactionResult storeAuxData(TransactionResult tResult)
    {
        String addressFunctionKey = tResult.contractAddress + "-" + tResult.attrId;
        if (!transactionResults.containsKey(tResult.contractChainId)) transactionResults.put(tResult.contractChainId, new HashMap<>());
        if (!transactionResults.get(tResult.contractChainId).containsKey(addressFunctionKey)) transactionResults.get(tResult.contractChainId).put(addressFunctionKey, new HashMap<>());
        Map<BigInteger, CachedResult> tokenResultMap = transactionResults.get(tResult.contractChainId).get(addressFunctionKey);
        tokenResultMap.put(tResult.tokenId, new CachedResult(tResult.resultTime, tResult.result));
        transactionResults.get(tResult.contractChainId).put(addressFunctionKey, tokenResultMap);

        return tResult;
    }

    //Not relevant for website - this function is to access wallet internal balance for tokens
    @Override
    public boolean resolveOptimisedAttr(ContractAddress contract, AttributeType attr, TransactionResult transactionResult)
    {
        return false;
    }

    @Override
    public String getWalletAddr()
    {
        return ZERO_ADDRESS;
    }

    /**
     * Can ditch this class once we have the transaction optimisation working as detailed in the "TO-DO" above
     */
    private class CachedResult
    {
        long resultTime;
        String result;

        CachedResult(long time, String r)
        {
            resultTime = time;
            result = r;
        }
    }

    private static void loadInfuraKey()
    {
        try (InputStream input = new FileInputStream("../gradle.properties")) {

            Properties prop = new Properties();

            if (input == null) {
                return;
            }

            //load a properties file from class path, inside static method
            prop.load(input);

            //get the property value and print it out
            infuraKey = prop.getProperty("infuraAPI").replaceAll("\"", "");

        } catch (IOException ex) {
            System.out.println("If you don't want to use our old key, place your Infura key in your gradle.properties file as infuraAPI=xxxxx");
        }
    }

    public static String getInfuraKey()
    {
        return infuraKey;
    }

    private Single<File> fetchTokenScriptFromServer(int chainId, String address)
    {
        return Single.fromCallable(() -> {
            File result = new File("");
            if (address.equals("")) return result;

            //peek to see if this file exists
            File existingFile = getTokenScriptFile(chainId, address);
            result = existingFile;
            long fileTime = 0;
            if (existingFile != null && existingFile.exists())
            {
                fileTime = existingFile.lastModified();
            }

            SimpleDateFormat format = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            String dateFormat = format.format(new Date(fileTime));

            StringBuilder sb = new StringBuilder();
            sb.append(TOKENSCRIPT_REPO_SERVER);
            sb.append(TOKENSCRIPT_CURRENT_SCHEMA);
            sb.append("/");
            sb.append(address);

            okhttp3.Response response = null;

            try
            {
                Request request = new Request.Builder()
                        .url(sb.toString())
                        .get()
                        .addHeader("Accept", "text/xml; charset=UTF-8")
                        .addHeader("X-Client-Name", "FileViewer")
                        .addHeader("X-Client-Version", "1")
                        .addHeader("X-Platform-Name", "FileViewer")
                        .addHeader("X-Platform-Version", "1")
                        .addHeader("If-Modified-Since", dateFormat)
                        .build();

                response = client.newCall(request).execute();

                switch (response.code())
                {
                    case HttpURLConnection.HTTP_NOT_MODIFIED:
                        break;
                    case HttpURLConnection.HTTP_OK:
                        String xmlBody = response.body().string();
                        result = storeFile(address, xmlBody);
                        break;
                    default:
                        break;
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            finally
            {
                if (response != null) response.body().close();
            }

            return result;
        });
    }

    private File storeFile(String address, String result) throws IOException
    {
        if (result == null || result.length() < 10) return null;

        String fName = address + ".tsml";

        File file = new File(repoDir.toString(), fName);

        FileOutputStream fos = new FileOutputStream(file);
        OutputStream     os  = new BufferedOutputStream(fos);
        os.write(result.getBytes());
        fos.flush();
        os.close();
        fos.close();

        return file;
    }
}