package com.xlson.groovycsv

import com.opencsv.CSVReader
import spock.lang.*

class CsvParserSpec extends Specification {
    def getTestDataWithColumnNamesAnd3Rows() {
        '''Name,Lastname,Age,Email
Mark,Hamilton,45,mark@hamilton.com
Bosse,Bildoktorn,50,bildoktorn@tv4.se
Peps,Persson,65,peps.persson@hotmail.com'''
    }

    def getTestDataWithColumnNamesAnd2Rows() {
        '''Letter,Word,Number
a,paris,5
h,drink,60'''
    }

    def getCsvWithColonAsSeparator() {
        '''Fruit:Count
Apple:5
Pear:10
Kiwi:200'''
    }

    def getTestDataWithQuotedComma() {
        '''a,b
-,abc-,4
abc,-4-'''
    }

    def "Iterating over the parsed csv values are available by column name."() {
        setup:
        def data = new CsvParser().parse(getTestDataWithColumnNamesAnd3Rows())

        expect:
        data*."$columnName" == values

        where:
        columnName | values
        "Name"     | ['Mark', 'Bosse', "Peps"]
        "Lastname" | ['Hamilton', 'Bildoktorn', 'Persson']
        'Age'      | ['45', '50', '65']
        "Email"    | ['mark@hamilton.com', 'bildoktorn@tv4.se', 'peps.persson@hotmail.com']
    }

    def "Functional collection methods are available on parsed object."() {
        setup:
        def data = new CsvParser().parse(getTestDataWithColumnNamesAnd3Rows())

        expect:
        data.findAll { (it.Age as int) > 46 }.size() == 2
    }

    def "readAll should never be called on the CSVReader instance used to parse the csv."() {
        setup:
        CSVReader csvReader = Mock(CSVReader)
        def partiallyMockedCsvParser = new CsvParser()
        partiallyMockedCsvParser.metaClass.createCSVReader = { Reader reader ->
            csvReader
        }

        when: "csv is parsed and looped through"
        def data = partiallyMockedCsvParser.parse(getTestDataWithColumnNamesAnd2Rows())
        for (d in data) {
        }

        then: "readAll() should not be called."
        0 * csvReader.readAll()
    }

    def "Parse supports a custom separator."() {
        setup:
        def data = new CsvParser().parse(csvWithColonAsSeparator, separator: ':')

        expect:
        data*."$columnName" == values

        where:
        columnName | values
        "Fruit"    | ['Apple', 'Pear', 'Kiwi']
        "Count"    | ["5", "10", "200"]
    }

    def getCsvUsingDoubleQuoteAsQuoteChar() {
        '''Typo,Desc
123,"text ,and more"'''
    }

    def getCsvUsingPercentageAsQuoteChar() {
        '''Typo,Desc
1123,%bla, ha%'''

    }

    def "Parse supports custom quote character."() {
        when:
        def csv = new CsvParser().parse(csvData, quoteChar: quoteChar)

        then:
        csv*."$columnName" == values

        where:
        csvData                        | quoteChar | values             | columnName
        csvUsingDoubleQuoteAsQuoteChar | '"'       | ['text ,and more'] | "Desc"
        csvUsingPercentageAsQuoteChar  | "%"       | ['bla, ha']        | "Desc"
    }

    def "Parse supports custom escape char."() {
        setup:
        def csvData = '''Test,It
1,"this is \"a quote\""'''
        def csv = new CsvParser().parse(csvData, escapeChar: "\\")

        expect:
        csv*.It == ['this is "a quote"']

    }

    def "Parser quietly ignores one empty line."() {
        setup:
        def csvData = '''Fruit,Country

Apple,Sweden
'''
        def csv = new CsvParser().parse(csvData)

        expect:
        for (line in csv) {
            line.Fruit == 'Apple'
            line.Country == 'Sweden'
        }
    }

    def "Parser quitely ignores multiple empty lines."() {
        setup:
        def csvData = '''Color,Day
Red,Monday



Black,Friday'''

        def csv = new CsvParser().parse(csvData)

        expect:
        def firstLine = csv.next()
        firstLine.Color == 'Red'
        firstLine.Day == 'Monday'

        def secondLine = csv.next()
        secondLine.Color == 'Black'
        secondLine.Day == 'Friday'
        csv.hasNext() == false
    }

    def "Parser quitely ignores multiple empty linessadadas."() {
        setup:
        def csvData = '''Color,Day



Red,Monday



Black,Friday


'''

        def csv = new CsvParser().parse(csvData)

        expect:
        for (line in csv) {
            println line
        }
    }

    def "Parse supports java.io.Reader as input."() {
        when:
        def csv = new CsvParser().parse(new StringReader(testDataWithColumnNamesAnd2Rows))

        then:
        csv*.Number == ['5', '60']
    }

    def "CsvParser can auto detect separator and quote character"() {
        when: "a CSV file is parsed with auto detection"
        def csv = new CsvParser().parse(autoDetect: true, csvData)

        then: "it should return the correct columns"
        csv*."$property" == values

        where:
        csvData                         | property | values
        testDataWithColumnNamesAnd3Rows | "Age"    | ["45", "50", "65"]
        csvWithColonAsSeparator         | "Count"  | ["5", "10", "200"]
        csvUsingDoubleQuoteAsQuoteChar  | "Desc"   | ["text ,and more"]
        csvUsingDoubleQuoteAsQuoteChar  | "Typo"   | ["123"]
        testDataWithColumnNamesAnd2Rows | "Word"   | ["paris", "drink"]
        testDataWithColumnNamesAnd3Rows | "Email"  | ["mark@hamilton.com", "bildoktorn@tv4.se", "peps.persson@hotmail.com"]
    }

    def "should allow to override auto detection"() {
        when: "autoDetect is active and a separator is provided"
        def csv = new CsvParser().parse(autoDetect: true, separator: ',', csvWithColonAsSeparator)

        then: "the separator provided is used"
        csv*."Fruit:Count" == ["Apple:5", "Pear:10", "Kiwi:200"]
    }

    def "The separator should be allowed in the csv data if its quoted"() {
        when:
        def csv = new CsvParser().parse(quoteChar: '-', testDataWithQuotedComma)

        then:
        csv*.a == [',abc', 'abc']
    }

    def "Values in the csv can be obtained by using the index of the column."() {
        when:
        def csv = new CsvParser().parse(testDataWithColumnNamesAnd2Rows)
        def line = csv.next()

        then:
        line[0] == 'a'
        line[1] == 'paris'
        line[2] == '5'
    }

    def csvWithoutHeaders = 'Joe,Doe,19'

    def "Parsing csv without headers using the position of the values."() {
        when:
        def csv = new CsvParser().parse(readFirstLine: true, csvWithoutHeaders)
        def line = csv.next()

        then:
        line[0] == 'Joe'
        line[1] == 'Doe'
        line[2] == '19'

    }

    def "CsvParser.parseCsv can be used statically."() {
        when:
        def csv = CsvParser.parseCsv(csvData)

        then:
        csv*.Letter == letterValues

        where:
        csvData                                           | letterValues
        testDataWithColumnNamesAnd2Rows                   | ['a', 'h']
        new StringReader(testDataWithColumnNamesAnd2Rows) | ['a', 'h']
    }

    def getTestDataWithWhitespaceSurroundingColumnNames() {
        """ Name , Lastname,Country 
Leonard,Gram,Sweden"""
    }

    def "CsvParser can strip leading and trailing whitespaces on column names."() {
        when:
        def csv = CsvParser.parseCsv(testDataWithWhitespaceSurroundingColumnNames, trimWhitespaceFromColumnNames: true)

        then:
        for(line in csv) {
            line.Name == 'Leonard'
            line.Lastname == 'Gram'
            line.Country == 'Sweden'
        }
    }

    def "CsvParser can skip lines at the beginning of the file."() {

       def csvWithSkipLines = '''skipped line1
skipped line2
Letter,Word,Number
a,paris,5
h,drink,60'''
        when:
        def csv = new CsvParser().parse(skipLines: 2, csvWithSkipLines)
        def line = csv.next()

        then:
        line["Letter"] == "a"
        line["Word"] == "paris"
        line["Number"] == "5"
    }

    def "CsvParser and different cardinality lines"() {
        given:
        def csvWithDifferentCardinality = '''header1,header2,header3
val1,val2
val1,val2,val3'''

        when:
        def csv = new CsvParser().parse(csvWithDifferentCardinality)
        def line = csv.next()

        then:
        line["header3"] == null

    }
}
