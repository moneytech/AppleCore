structure Parser : PARSER =
struct

open Error

type line = (Label.t option) * (Instruction.t option)

fun parseInstruction substr =
    case Instruction.parse substr of
        SOME (Instruction.Directive Directives.Ignored) => NONE
      |	SOME i => SOME i
      | _      =>
	let
	    val rest = Substring.dropl Char.isSpace substr
	in
	    if Substring.isEmpty rest
	    then NONE
	    else raise InvalidMnemonic 
			   (Substring.string (Substring.takel (not o Char.isSpace) rest))
	end
		       
fun parseNoLabel substr =
    case parseInstruction substr of
	SOME i => SOME (NONE, SOME i)
      | _      => NONE

fun parseLabel substr =
    case Label.parse substr of
	NONE => raise Error.BadLabel
      | SOME (l,rest) => SOME (SOME l,parseInstruction rest)

fun parseLine line =
    let
	val substr = Substring.full line
    in
	case Substring.getc substr of
	    NONE => NONE
	  | SOME (c,rest) =>
	    if Char.isSpace c 
	    then parseNoLabel rest
	    else 
		if c = #"*" orelse c = #":" then NONE
		else parseLabel substr
    end

fun parseAll file =
    let
	fun parseAll' stream n =
	    case TextIO.inputLine stream of
		SOME line => 
		(ignore (parseLine line) handle e => 
						(Error.show n e; print line);
		 parseAll' stream (n + 1))
	      | NONE => ()
    in
	parseAll' (TextIO.openIn file) 1
    end

end

  
