structure Instruction : INSTRUCTION =
  struct

  open Error

  datatype t =
	   Native of Native.t
	 | Directive of Directive.t
	 | AppleCore of AppleCore.t

  fun apply parser substr =
      parser (Substring.dropl Char.isSpace substr)
			
  fun parse' substr = 
      case apply Native.parse substr of
	  SOME inst => SOME (Native inst)
        | _  => (case apply AppleCore.parse substr of
		     SOME inst => SOME (AppleCore inst)
		   | _ => (case apply Directive.parse substr of
			       SOME inst => SOME (Directive inst)
			     | _ => NONE))

  fun parse substr =
      case parse' substr of
	  SOME i => SOME i
	| _  =>
	  let
	      val rest = Substring.dropl Char.isSpace substr
	  in
	      if Substring.isEmpty rest
	      then NONE
	      else raise InvalidMnemonic 
			     (Substring.string (Substring.takel (not o Char.isSpace) rest))
	  end

  fun includeIn paths file inst =
      case inst of
	  Directive d =>
	  Directive.includeIn paths file d
	| _ => file
		       
  end

  
